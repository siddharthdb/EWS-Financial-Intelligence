package org.ewsfi.policy.validation;

import com.fasterxml.jackson.databind.JsonNode;
import com.networknt.schema.*;
import java.util.*;

/** Reusable Draft 2020-12 structural contract gate. */
public final class JsonSchemaGate {
  private final JsonSchema schema;
  public JsonSchemaGate(JsonNode schemaNode){schema=JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(schemaNode);}
  public Set<ValidationMessage> validate(JsonNode instance){return schema.validate(instance);}
  public void requireValid(JsonNode instance,String label){
    Set<ValidationMessage> errors=validate(instance);
    if(!errors.isEmpty()) throw new IllegalArgumentException(label+" failed JSON Schema validation: "+errors);
  }
}