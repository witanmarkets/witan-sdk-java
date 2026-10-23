package markets.witan.sdk;

/** A hit of {@link Witan#searchError}: a unit and how it matched the error. */
public final class ErrorHit extends SearchHit {
  public ErrorMatch match;
}
