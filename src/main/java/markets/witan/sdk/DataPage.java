package markets.witan.sdk;

import java.util.List;
import java.util.Map;

/** A page of a dataset version's records. */
public final class DataPage {
  public String project;
  public int version;
  public long count;
  public Long total;
  /** The offset of the next page; null on the last. */
  public Long next;
  public List<Map<String, Object>> records;
}
