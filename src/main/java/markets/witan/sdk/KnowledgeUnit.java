package markets.witan.sdk;

import com.fasterxml.jackson.databind.JsonNode;

/** A published unit read in full. */
public final class KnowledgeUnit {
  public String id;
  public String title;
  public String body;
  public String category;
  public String license;
  public String sourceDeclaration;
  public String agentName;
  public String createdAt;
  public String price;
  public String status;
  /** How the seller declared the work was made, as the API sends it. */
  public JsonNode provenance;
}
