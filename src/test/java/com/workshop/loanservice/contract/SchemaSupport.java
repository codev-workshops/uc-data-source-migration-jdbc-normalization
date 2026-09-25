package com.workshop.loanservice.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SchemaLocation;
import com.networknt.schema.SchemaValidatorsConfig;
import com.networknt.schema.SpecVersion.VersionFlag;
import com.networknt.schema.ValidationMessage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Set;

/** Loads JSON Schemas from {@code src/test/resources/schemas} and validates response bodies. */
final class SchemaSupport {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String BASE = "classpath:schemas/";

  private static final JsonSchemaFactory FACTORY =
      JsonSchemaFactory.getInstance(
          VersionFlag.V202012,
          builder ->
              builder.schemaMappers(
                  mappers -> mappers.mappings(iri -> iri.contains(":") ? iri : BASE + iri)));

  private SchemaSupport() {}

  static JsonSchema schema(String name) {
    SchemaValidatorsConfig config = new SchemaValidatorsConfig();
    config.setTypeLoose(false);
    return FACTORY.getSchema(SchemaLocation.of(BASE + name + ".schema.json"), config);
  }

  static JsonNode parse(String json) {
    try {
      return MAPPER.readTree(json);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
  }

  /** Asserts {@code json} conforms to the named schema, treating an array body as a list. */
  static JsonNode assertConforms(String json, String schemaName) {
    JsonNode node = parse(json);
    JsonSchema schema = schema(schemaName);
    if (node.isArray()) {
      for (JsonNode item : node) {
        assertNoErrors(schema.validate(item), item);
      }
    } else {
      assertNoErrors(schema.validate(node), node);
    }
    return node;
  }

  private static void assertNoErrors(Set<ValidationMessage> errors, JsonNode node) {
    assertThat(errors).as("schema violations for %s", node).isEmpty();
  }
}
