package markets.witan.sdk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class WitanTest {
  private HttpServer server;

  interface Handler {
    void handle(HttpExchange ex) throws IOException;
  }

  private Witan serve(Handler h) throws IOException {
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext("/", ex -> h.handle(ex));
    server.start();
    return Witan.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).apiKey("").retries(2).build();
  }

  @AfterEach
  void stop() {
    if (server != null) server.stop(0);
  }

  static void reply(HttpExchange ex, int status, String body, String... headers) throws IOException {
    for (int i = 0; i + 1 < headers.length; i += 2) ex.getResponseHeaders().add(headers[i], headers[i + 1]);
    byte[] b = body.getBytes(StandardCharsets.UTF_8);
    ex.sendResponseHeaders(status, b.length == 0 ? -1 : b.length);
    if (b.length > 0) ex.getResponseBody().write(b);
    ex.close();
  }

  @Test
  void searchErrorSendsTheErrorInTheBody() throws Exception {
    AtomicReference<String> seen = new AtomicReference<>();
    Witan w =
        serve(
            ex -> {
              JsonNode body = Witan.JSON.readTree(ex.getRequestBody());
              seen.set(ex.getRequestMethod() + " " + ex.getRequestURI() + " " + body.get("error").asText() + " "
                  + body.get("limit").asInt() + " " + ex.getRequestHeaders().getFirst("user-agent"));
              reply(ex, 200, "{\"signature\":\"s\",\"mode\":\"error\",\"results\":[{\"id\":\"u1\",\"title\":\"T\",\"score\":\"78.00\","
                  + "\"priceMicro\":\"0\",\"match\":{\"kind\":\"exact\",\"similarity\":1},\"somethingNew\":true}]}");
            });
    ErrorSearchAnswer a = w.searchError("npm error code ENEEDAUTH", null, 3);
    assertEquals("POST /search/error npm error code ENEEDAUTH 3 witan-sdk-java/" + Witan.VERSION, seen.get());
    ErrorHit h = a.results.get(0);
    assertEquals("u1", h.id);
    assertEquals(78.0, h.score);
    assertEquals("exact", h.match.kind);
    assertEquals(0.0, h.priceMicro);
  }

  @Test
  void searchSendsQueryAndKey() throws Exception {
    AtomicReference<String> seen = new AtomicReference<>();
    Witan w =
        serve(
            ex -> {
              seen.set(ex.getRequestURI().getRawQuery() + " " + ex.getRequestHeaders().getFirst("authorization"));
              reply(ex, 200, "{\"results\":[{\"id\":\"a\",\"score\":null},{\"id\":\"b\",\"score\":73}]}");
            });
    Witan keyed = Witan.builder().baseUrl("http://127.0.0.1:" + server.getAddress().getPort()).apiKey("km_test").build();
    List<SearchHit> hits = keyed.search("redis throughput", "keyword", null, 5);
    assertEquals("q=redis+throughput&mode=keyword&limit=5 Bearer km_test", seen.get());
    assertNull(hits.get(0).score);
    assertEquals(73.0, hits.get(1).score);
  }

  @Test
  void readPaymentRequired() throws Exception {
    Witan w =
        serve(ex -> reply(ex, 402, "{\"error\":\"payment required\",\"price\":\"$0.01\","
            + "\"pay\":\"https://witan.markets/paid/knowledge?id=x\",\"freeWithKey\":true}"));
    PaymentRequiredException e = assertThrows(PaymentRequiredException.class, () -> w.read("x"));
    assertEquals(402, e.status);
    assertEquals("$0.01", e.price);
    assertTrue(e.pay.contains("paid/knowledge"));
    assertTrue(e.freeWithKey);
  }

  @Test
  void retries503ThenSucceeds() throws Exception {
    AtomicInteger n = new AtomicInteger();
    Witan w =
        serve(ex -> {
          if (n.incrementAndGet() < 3) reply(ex, 503, "", "retry-after", "0");
          else reply(ex, 200, "{\"id\":\"u1\",\"title\":\"T\",\"body\":\"B\"}");
        });
    assertEquals("B", w.read("u1").body);
    assertEquals(3, n.get());
  }

  @Test
  void givesUpAfterRetriesAndKeepsTheMessage() throws Exception {
    AtomicInteger n = new AtomicInteger();
    Witan w = serve(ex -> { n.incrementAndGet(); reply(ex, 429, "{\"error\":\"slow down\"}", "retry-after", "0"); });
    WitanException e = assertThrows(WitanException.class, w::datasets);
    assertEquals(429, e.status);
    assertTrue(e.getMessage().contains("slow down"));
    assertEquals(3, n.get());
  }

  @Test
  void noRetryOn404AndSlugIsOneSegment() throws Exception {
    AtomicInteger n = new AtomicInteger();
    AtomicReference<String> path = new AtomicReference<>();
    Witan w = serve(ex -> { n.incrementAndGet(); path.set(ex.getRequestURI().getRawPath()); reply(ex, 404, "{\"error\":\"not found\"}"); });
    WitanException e = assertThrows(WitanException.class, () -> w.dataset("a/b"));
    assertEquals(404, e.status);
    assertEquals(1, n.get());
    assertEquals("/projects/a%2Fb", path.get());
  }

  @Test
  void queryBody() throws Exception {
    AtomicReference<String> seen = new AtomicReference<>();
    Witan w =
        serve(ex -> {
          seen.set(Witan.JSON.readTree(ex.getRequestBody()).toString());
          reply(ex, 200, "{\"columns\":[\"x\"],\"rows\":[[1]],\"count\":1}");
        });
    QueryResult q = w.query("agent-api-observatory", "SELECT 1", 79, null);
    assertEquals("{\"sql\":\"SELECT 1\",\"version\":79}", seen.get());
    assertEquals(List.of("x"), q.columns);
    assertEquals(1, q.count);
  }
}
