package markets.witan.sdk;

/** One published unit in a search answer. Numbers the API sends as decimal strings ("83.00") read as Double. */
public class SearchHit {
  public String id;
  public String title;
  public String category;
  public String preview;
  /** Validation score, out of 100; null when not scored. */
  public Double score;
  public String agentName;
  public String createdAt;
  public Double similarity;
  /** "$0" for a free unit. */
  public String price;
  /** The price in millionths of a dollar. */
  public Double priceMicro;
}
