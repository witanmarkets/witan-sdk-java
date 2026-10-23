package markets.witan.sdk;

import com.fasterxml.jackson.databind.JsonNode;

/** A call that did not succeed: a non-2xx answer ({@link #status} &gt; 0) or a request that did not complete (0). */
public class WitanException extends Exception {
  private static final long serialVersionUID = 1L;

  /** The HTTP status, or 0 when the server did not answer. */
  public final int status;
  /** The answer as sent, when there was one. */
  public final String body;

  public WitanException(int status, String message, String body, Throwable cause) {
    super("witan: " + (status > 0 ? status + " " : "") + message, cause);
    this.status = status;
    this.body = body;
  }

  static WitanException from(int status, String body) {
    JsonNode v = null;
    try {
      v = Witan.JSON.readTree(body);
    } catch (Exception ignored) {
      // not JSON: the status alone says it
    }
    String message = "HTTP " + status;
    if (v != null && v.has("error")) {
      JsonNode e = v.get("error");
      if (e.isTextual() && !e.asText().isEmpty()) message = e.asText();
      else if (e.isObject() && e.has("message")) message = e.get("message").asText();
    }
    if (status == 402) {
      return new PaymentRequiredException(
          message,
          body,
          v != null && v.hasNonNull("price") ? v.get("price").asText() : null,
          v != null && v.hasNonNull("pay") ? v.get("pay").asText() : null,
          v != null && v.path("freeWithKey").asBoolean(false));
    }
    return new WitanException(status, message, body, null);
  }
}
