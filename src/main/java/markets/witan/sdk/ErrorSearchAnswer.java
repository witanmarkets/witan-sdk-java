package markets.witan.sdk;

import java.util.List;

/** The answer of {@link Witan#searchError}: the error's signature and the units that cover it. */
public final class ErrorSearchAnswer {
  public String signature;
  public String mode;
  public List<ErrorHit> results;
}
