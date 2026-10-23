package markets.witan.sdk;

import java.util.List;

/** The answer of {@link Witan#query}. */
public final class QueryResult {
  public String project;
  public int version;
  public List<String> columns;
  public List<String> types;
  public List<List<Object>> rows;
  public long count;
  public boolean truncated;
  public double ms;
  public long scannedBytes;
}
