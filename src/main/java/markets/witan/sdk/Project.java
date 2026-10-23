package markets.witan.sdk;

/** A dataset project in the list. */
public final class Project {
  public String slug;
  public String title;
  public String status;
  public String license;
  /** "public" or "paid". */
  public String access;
  public String visibility;
  public String createdAt;
  public long stars;
  public long contributions;
  public long records;
  public int latestVersion;
}
