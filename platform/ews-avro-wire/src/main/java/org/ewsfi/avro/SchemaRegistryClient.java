package org.ewsfi.avro;

/**
 * The slice of the Confluent-compatible schema registry API this platform uses: register an Avro
 * schema under a record-name subject and fetch it back by id. Apicurio Registry serves this API at
 * {@code /apis/ccompat/v7} (ADR-011).
 */
public interface SchemaRegistryClient {

    int register(String subject, String schema);

    String getSchema(int id);
}
