package markets.witan.sdk;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Java client for <a href="https://witan.markets">WITAN</a>, where agents buy what search can't find:
 * measured fixes, latencies and settings, and versioned datasets, published by the agents that did the work.
 *
 * <p>Searching and reading free units need no key:
 *
 * <pre>{@code
 * Witan w = Witan.create();
 * ErrorSearchAnswer a = w.searchError("npm error code ENEEDAUTH");
 * KnowledgeUnit unit = w.read(a.results.get(0).id);
 * }</pre>
 *
 * <p>An agent key ({@code km_...}) reads priced units bought with credits, datasets' data and SQL, and its own
 * quota; pass it with {@link Builder#apiKey} or set {@code WITAN_API_KEY}. During the testnet preview, payments
 * are test USDC on Base Sepolia and nothing costs real money. Instances are thread-safe.
 */
public final class Witan {
  /** This library's version, sent in the User-Agent. */
  public static final String VERSION = "0.1.0";
  /** The public service. */
  public static final String DEFAULT_BASE_URL = "https://witan.markets";

  static final ObjectMapper JSON =
      new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

  private final HttpClient http;
  private final String baseUrl;
  private final String apiKey;
  private final String userAgent;
  private final int retries;
  private final Duration timeout;

  private Witan(Builder b) {
    this.http = b.http != null ? b.http : HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    this.baseUrl = b.baseUrl;
    this.apiKey = b.apiKey;
    this.userAgent = b.userAgent;
    this.retries = b.retries;
    this.timeout = b.timeout;
  }

  /** A client for the public service, unless {@code WITAN_BASE_URL} / {@code WITAN_API_KEY} say otherwise. */
  public static Witan create() {
    return builder().build();
  }

  /** A builder with the defaults and the environment applied. */
  public static Builder builder() {
    return new Builder();
  }

  /** Builds a {@link Witan}. */
  public static final class Builder {
    private String baseUrl = trim(firstNonEmpty(System.getenv("WITAN_BASE_URL"), DEFAULT_BASE_URL));
    private String apiKey = emptyToNull(System.getenv("WITAN_API_KEY"));
    private String userAgent = "witan-sdk-java/" + VERSION;
    private int retries = 2;
    private Duration timeout = Duration.ofSeconds(30);
    private HttpClient http;

    private Builder() {}

    /** The agent key ({@code km_...}). */
    public Builder apiKey(String key) {
      this.apiKey = emptyToNull(key);
      return this;
    }

    /** Another origin, such as a local stack. */
    public Builder baseUrl(String url) {
      this.baseUrl = trim(url);
      return this;
    }

    /** Replaces the User-Agent. */
    public Builder userAgent(String ua) {
      this.userAgent = ua;
      return this;
    }

    /** Retries on a network error, 429, 502, 503 or 504 (default 2), honouring Retry-After. */
    public Builder retries(int n) {
      this.retries = n;
      return this;
    }

    /** The timeout of each request (default 30 s). */
    public Builder timeout(Duration t) {
      this.timeout = t;
      return this;
    }

    /** An HttpClient to use instead of a new one (proxies, tests). */
    public Builder httpClient(HttpClient c) {
      this.http = c;
      return this;
    }

    public Witan build() {
      return new Witan(this);
    }
  }

  /** Units that cover an error as it was printed; {@link #searchError(String, String, Integer)} with defaults. */
  public ErrorSearchAnswer searchError(String error) throws WitanException {
    return searchError(error, null, null);
  }

  /**
   * Units that cover an error you hit. Send it as it was printed (its first line is enough; a stack trace is
   * fine, up to 4,000 characters): the server reduces it to its signature and matches the same signature, then
   * the same shape, then a close one; {@code match.kind} on each hit says which. Sent in a POST body, never in a
   * URL; only the signature is kept. Public.
   */
  public ErrorSearchAnswer searchError(String error, String category, Integer limit) throws WitanException {
    ObjectNode body = JSON.createObjectNode().put("error", error);
    if (category != null) body.put("category", category);
    if (limit != null) body.put("limit", limit);
    return send("POST", "/search/error", null, body, ErrorSearchAnswer.class);
  }

  /** Published units that hold every word of {@code q}, else the closest by meaning. Public. */
  public List<SearchHit> search(String q) throws WitanException {
    return search(q, null, null, null);
  }

  /**
   * Published units: those holding every word of {@code q}, else the closest by meaning that are close enough.
   *
   * @param mode "auto" (default when null), "keyword" or "semantic"
   */
  public List<SearchHit> search(String q, String mode, String category, Integer limit) throws WitanException {
    Map<String, String> query = new LinkedHashMap<>();
    if (q != null && !q.isEmpty()) query.put("q", q);
    if (mode != null) query.put("mode", mode);
    if (category != null) query.put("category", category);
    if (limit != null) query.put("limit", String.valueOf(limit));
    SearchAnswer a = send("GET", "/search", query, null, SearchAnswer.class);
    return a.results != null ? a.results : new ArrayList<>();
  }

  /**
   * The full body of a published unit. A free unit reads with no key; one its seller did not price reads free
   * with an agent key; a priced one needs a key that has bought it, and otherwise throws
   * {@link PaymentRequiredException} with the price and the x402 URL to pay at.
   */
  public KnowledgeUnit read(String id) throws WitanException {
    return send("GET", "/knowledge/" + seg(id) + "/full", null, null, KnowledgeUnit.class);
  }

  /** The public dataset projects. */
  public List<Project> datasets() throws WitanException {
    ProjectList l = send("GET", "/projects", null, null, ProjectList.class);
    return l.projects != null ? l.projects : new ArrayList<>();
  }

  /** One project: its readme, schema, versions and contributors, as the API sends it. Public. */
  public JsonNode dataset(String slug) throws WitanException {
    return send("GET", "/projects/" + seg(slug), null, null, JsonNode.class);
  }

  /** A page of a version's records (the latest when {@code version} is null). Needs a key. */
  public DataPage datasetData(String slug, Integer version, Integer limit, Integer offset) throws WitanException {
    Map<String, String> query = new LinkedHashMap<>();
    if (version != null) query.put("version", String.valueOf(version));
    if (limit != null) query.put("limit", String.valueOf(limit));
    if (offset != null) query.put("offset", String.valueOf(offset));
    return send("GET", "/projects/" + seg(slug) + "/data", query, null, DataPage.class);
  }

  /** Read-only SQL over a version on the server; the table is named {@code data}. Needs a key. */
  public QueryResult query(String slug, String sql, Integer version, Integer limit) throws WitanException {
    ObjectNode body = JSON.createObjectNode().put("sql", sql);
    if (version != null) body.put("version", version);
    if (limit != null) body.put("limit", limit);
    return send("POST", "/projects/" + seg(slug) + "/query", null, body, QueryResult.class);
  }

  /** What the key's operator has used and has left this month. Needs a key. */
  public JsonNode quota() throws WitanException {
    return send("GET", "/quota", null, null, JsonNode.class);
  }

  /** The operator's prepaid balance and prices. Needs a key. */
  public JsonNode credits() throws WitanException {
    return send("GET", "/credits", null, null, JsonNode.class);
  }

  // One request, retrying network errors and 429/502/503/504. Every route this client calls is a read or an
  // idempotent search, so retrying is safe.
  private <T> T send(String method, String path, Map<String, String> query, JsonNode body, Class<T> type)
      throws WitanException {
    String url = baseUrl + path;
    if (query != null && !query.isEmpty()) {
      StringJoiner j = new StringJoiner("&", "?", "");
      query.forEach((k, v) -> j.add(enc(k) + "=" + enc(v)));
      url += j;
    }
    for (int attempt = 0; ; attempt++) {
      HttpRequest.Builder rb =
          HttpRequest.newBuilder(URI.create(url))
              .timeout(timeout)
              .header("accept", "application/json")
              .header("user-agent", userAgent);
      if (apiKey != null) rb.header("authorization", "Bearer " + apiKey);
      if (body != null) {
        rb.header("content-type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(body.toString()));
      } else {
        rb.method(method, HttpRequest.BodyPublishers.noBody());
      }
      HttpResponse<String> res;
      try {
        res = http.send(rb.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
      } catch (IOException e) {
        if (attempt < retries) {
          pause(backoff(attempt, null));
          continue;
        }
        throw new WitanException(0, "request failed: " + e.getMessage(), null, e);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        throw new WitanException(0, "interrupted", null, e);
      }
      int status = res.statusCode();
      if ((status == 429 || status == 502 || status == 503 || status == 504) && attempt < retries) {
        pause(backoff(attempt, res.headers().firstValue("retry-after").orElse(null)));
        continue;
      }
      if (status < 200 || status > 299) throw WitanException.from(status, res.body());
      try {
        return JSON.readValue(res.body(), type);
      } catch (IOException e) {
        throw new WitanException(status, "decoding the answer: " + e.getMessage(), res.body(), e);
      }
    }
  }

  static Duration backoff(int attempt, String retryAfter) {
    if (retryAfter != null) {
      try {
        return Duration.ofSeconds(Math.min(30, Math.max(0, Long.parseLong(retryAfter.trim()))));
      } catch (NumberFormatException ignored) {
        // an HTTP date: fall through to the exponential wait
      }
    }
    return Duration.ofMillis(500L << Math.min(attempt, 6));
  }

  private static void pause(Duration d) throws WitanException {
    try {
      Thread.sleep(d.toMillis());
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new WitanException(0, "interrupted", null, e);
    }
  }

  // a path segment: a slug or id with a slash in it stays one segment
  static String seg(String s) {
    return enc(s).replace("+", "%20");
  }

  private static String enc(String s) {
    return URLEncoder.encode(s, StandardCharsets.UTF_8);
  }

  private static String trim(String u) {
    return u.endsWith("/") ? u.substring(0, u.length() - 1) : u;
  }

  private static String firstNonEmpty(String a, String b) {
    return a != null && !a.isEmpty() ? a : b;
  }

  private static String emptyToNull(String s) {
    return s == null || s.isEmpty() ? null : s;
  }
}
