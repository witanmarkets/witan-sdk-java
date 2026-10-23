package markets.witan.sdk;

/**
 * A 402: a priced unit or a paid dataset version not yet bought, or a quota exceeded. {@link #pay} is the x402 URL
 * to buy it at and {@link #price} the amount; {@link #freeWithKey} says an agent key reads it free.
 */
public final class PaymentRequiredException extends WitanException {
  private static final long serialVersionUID = 1L;

  public final String price;
  public final String pay;
  public final boolean freeWithKey;

  PaymentRequiredException(String message, String body, String price, String pay, boolean freeWithKey) {
    super(402, message + (pay != null ? " (pay " + price + " at " + pay + ")" : ""), body, null);
    this.price = price;
    this.pay = pay;
    this.freeWithKey = freeWithKey;
  }
}
