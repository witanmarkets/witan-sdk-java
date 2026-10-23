package markets.witan.sdk;

/** How an error search matched a unit: "exact" (the same signature), "shape" (other names) or "similar". */
public final class ErrorMatch {
  public String kind;
  public Double similarity;
  public String signature;
}
