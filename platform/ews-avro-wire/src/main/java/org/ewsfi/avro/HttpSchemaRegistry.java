package org.ewsfi.avro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Client for Apicurio Registry's Confluent-compatible API ({@code /apis/ccompat/v7}). Subjects are
 * the Avro record full name (record-name strategy), not the Kafka topic, because canonical topics
 * carry more than one record type.
 */
final class HttpSchemaRegistry implements SchemaRegistryClient {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String baseUrl;
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
    private final ConcurrentHashMap<String, Integer> idsBySchema = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<Integer, String> schemasById = new ConcurrentHashMap<>();

    HttpSchemaRegistry(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    }

    @Override
    public int register(String subject, String schema) {
        String cacheKey = subject + "\n" + schema;
        Integer cached = idsBySchema.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ObjectNode body = MAPPER.createObjectNode();
        body.put("schema", schema);
        body.put("schemaType", "AVRO");
        HttpResponse<String> response = send(
                "POST",
                "/subjects/" + URLEncoder.encode(subject, StandardCharsets.UTF_8) + "/versions",
                body.toString());
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException(
                    "Schema registration failed for " + subject + " (" + response.statusCode() + "): " + response.body());
        }
        int id = readId(response.body());
        idsBySchema.put(cacheKey, id);
        schemasById.put(id, schema);
        return id;
    }

    @Override
    public String getSchema(int id) {
        String cached = schemasById.get(id);
        if (cached != null) {
            return cached;
        }
        HttpResponse<String> response = send("GET", "/schemas/ids/" + id, null);
        if (response.statusCode() / 100 != 2) {
            throw new IllegalStateException("Schema id " + id + " was not found (" + response.statusCode() + ")");
        }
        try {
            JsonNode node = MAPPER.readTree(response.body());
            String schema = node.get("schema").asText();
            schemasById.put(id, schema);
            return schema;
        } catch (Exception e) {
            throw new IllegalStateException("Could not read schema id " + id, e);
        }
    }

    private int readId(String body) {
        try {
            return MAPPER.readTree(body).get("id").asInt();
        } catch (Exception e) {
            throw new IllegalStateException("Schema registry response had no id: " + body, e);
        }
    }

    private HttpResponse<String> send(String method, String path, String body) {
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(baseUrl + path))
                    .timeout(Duration.ofSeconds(10))
                    .header("Accept", "application/vnd.schemaregistry.v1+json");
            if (body == null) {
                builder.GET();
            } else {
                builder.header("Content-Type", "application/vnd.schemaregistry.v1+json")
                        .POST(HttpRequest.BodyPublishers.ofString(body));
            }
            return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            throw new IllegalStateException("Schema registry call " + method + " " + path + " failed", e);
        }
    }
}
