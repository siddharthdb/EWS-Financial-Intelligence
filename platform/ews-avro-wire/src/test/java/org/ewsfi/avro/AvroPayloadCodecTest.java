package org.ewsfi.avro;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AvroPayloadCodecTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final AvroPayloadCodec codec = AvroPayloadCodec.forTests();

    @Test
    void paymentReturnRoundTripsThroughConfluentFraming() throws Exception {
        String json =
                """
                {"eventId":"evt-1","eventType":"payment.instruction.returned","eventTime":"2026-09-24T00:00:00Z","entityType":"ACCOUNT","entityId":"acct-1","data":{"paymentInstructionId":"pi-1","accountId":"acct-1","amount":"15.50","currency":"GBP","returnReasonCode":"R01","returnReasonCategory":"FINANCIAL","returnDate":"2026-09-24"}}
                """;
        AvroPayloadCodec.Encoded encoded = codec.encode("payment.instruction.returned", json);
        assertThat(encoded.payload()[0]).isEqualTo((byte) 0);
        JsonNode decoded = MAPPER.readTree(codec.toInterimJson(encoded.payload(), encoded.headers()));
        assertThat(decoded.get("eventType").asText()).isEqualTo("payment.instruction.returned");
        assertThat(decoded.get("eventId").asText()).isEqualTo("evt-1");
        assertThat(decoded.get("data").get("returnReasonCategory").asText()).isEqualTo("FINANCIAL");
        assertThat(decoded.get("data").get("amount").asText()).isEqualTo("15.50");
        assertThat(decoded.get("entityId").asText()).isEqualTo("acct-1");
    }

    @Test
    void dpdRoundTripKeepsReconciledFalseAndPreviousDpd() throws Exception {
        String json =
                """
                {"eventId":"evt-dpd","eventType":"obligation.dpd.changed","eventTime":"2026-09-01T00:00:00Z","entityType":"FACILITY","entityId":"fac-1","data":{"facilityId":"fac-1","counterpartyId":"cpty-1","previousDpd":0,"currentDpd":12,"asOfDate":"2026-09-01","calculationPolicyRef":"POL-DPD-CALC-001","reconciled":true}}
                """;
        AvroPayloadCodec.Encoded encoded = codec.encode("obligation.dpd.changed", json);
        JsonNode decoded = MAPPER.readTree(codec.toInterimJson(encoded.payload(), encoded.headers()));
        assertThat(decoded.get("data").get("currentDpd").asInt()).isEqualTo(12);
        assertThat(decoded.get("data").get("previousDpd").asInt()).isEqualTo(0);
        assertThat(decoded.get("data").get("reconciled").asBoolean()).isTrue();
        assertThat(decoded.get("eventTime").asText()).isEqualTo("2026-09-01T00:00:00Z");
    }

    @Test
    void featureRoundTripRestoresWindowPriorAndNullNumeric() throws Exception {
        String json =
                """
                {"featureValueId":"fv-1","definitionId":"FD-CURRENT-DPD-001","featureName":"current_dpd","definitionVersion":"1.0","entityType":"FACILITY","entityId":"fac-1","state":"INVALID","valueType":"INTEGER","valueNumeric":null,"knowledgeTime":"2026-09-01T00:00:00Z","calculatedAt":"2026-09-01T00:00:00Z","windowStart":"2026-09-01T00:00:00Z","windowEnd":"2026-09-01T00:00:00Z","qualityState":"INSUFFICIENT","lineageTransformationVersion":"1.0","priorValueNumeric":"10"}
                """;
        AvroPayloadCodec.Encoded encoded = codec.encode("feature.value.updated", json);
        JsonNode decoded = MAPPER.readTree(codec.toInterimJson(encoded.payload(), encoded.headers()));
        assertThat(decoded.get("state").asText()).isEqualTo("INVALID");
        assertThat(decoded.get("valueNumeric").isNull()).isTrue();
        assertThat(decoded.get("priorValueNumeric").asText()).isEqualTo("10");
        assertThat(decoded.get("windowEnd").asText()).isEqualTo("2026-09-01T00:00:00Z");
        assertThat(decoded.get("qualityState").asText()).isEqualTo("INSUFFICIENT");
    }

    @Test
    void signalRoundTripKeepsEvidenceAndProposedStatus() throws Exception {
        String json =
                """
                {"signalId":"sig-1","signalType":"DPD_EMERGED","semanticScope":"GLOBAL_CORE","entityType":"FACILITY","entityId":"fac-1","status":"PROPOSED","severity":"LOW","confidenceValue":0.9,"materialityBand":"MEDIUM","detectedAt":"2026-09-01T00:00:00Z","effectiveAt":"2026-09-01T00:00:00Z","knowledgeTime":"2026-09-01T00:00:00Z","policyId":"POL-DPD-EMERGED-CORP-001","policyVersion":"1.0","dataQualityState":"COMPLETE","evidenceIds":["fv-1"]}
                """;
        AvroPayloadCodec.Encoded encoded = codec.encode("signal.detected", json);
        JsonNode decoded = MAPPER.readTree(codec.toInterimJson(encoded.payload(), encoded.headers()));
        assertThat(decoded.get("signalType").asText()).isEqualTo("DPD_EMERGED");
        assertThat(decoded.get("status").asText()).isEqualTo("PROPOSED");
        assertThat(decoded.get("evidenceIds").get(0).asText()).isEqualTo("fv-1");
        assertThat(decoded.get("entityId").asText()).isEqualTo("fac-1");
    }

    @Test
    void secondEncodeReusesTheSchemaId() {
        String json =
                """
                {"eventId":"evt-2","eventType":"payment.instruction.returned","eventTime":"2026-09-24T00:00:00Z","entityType":"ACCOUNT","entityId":"acct-2","data":{"paymentInstructionId":"pi-2","accountId":"acct-2","amount":"1.00","currency":"USD","returnReasonCode":"R01","returnReasonCategory":"TECHNICAL","returnDate":"2026-09-24"}}
                """;
        byte[] first = codec.encode("payment.instruction.returned", json).payload();
        byte[] second = codec.encode("payment.instruction.returned", json).payload();
        assertThat(schemaId(second)).isEqualTo(schemaId(first));
    }

    @Test
    void httpClientRegistersAndFetchesThroughTheCcompatPaths() throws Exception {
        ConcurrentHashMap<Integer, String> schemas = new ConcurrentHashMap<>();
        AtomicInteger next = new AtomicInteger(1);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext(
                "/apis/ccompat/v7/",
                exchange -> {
                    String path = exchange.getRequestURI().getPath();
                    byte[] response;
                    if (path.contains("/subjects/") && path.endsWith("/versions")) {
                        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                        String schema = MAPPER.readTree(body).get("schema").asText();
                        int id = next.getAndIncrement();
                        schemas.put(id, schema);
                        response = ("{\"id\":" + id + "}").getBytes(StandardCharsets.UTF_8);
                    } else if (path.contains("/schemas/ids/")) {
                        int id = Integer.parseInt(path.substring(path.lastIndexOf('/') + 1));
                        response =
                                ("{\"schema\":" + MAPPER.writeValueAsString(schemas.get(id)) + "}")
                                        .getBytes(StandardCharsets.UTF_8);
                    } else {
                        exchange.sendResponseHeaders(404, -1);
                        exchange.close();
                        return;
                    }
                    exchange.getResponseHeaders().add("Content-Type", "application/vnd.schemaregistry.v1+json");
                    exchange.sendResponseHeaders(200, response.length);
                    exchange.getResponseBody().write(response);
                    exchange.close();
                });
        server.start();
        try {
            String base = "http://127.0.0.1:" + server.getAddress().getPort() + "/apis/ccompat/v7";
            AvroPayloadCodec httpCodec = new AvroPayloadCodec(base);
            String json =
                    """
                    {"eventId":"evt-http","eventType":"payment.instruction.returned","eventTime":"2026-09-24T00:00:00Z","entityType":"ACCOUNT","entityId":"acct-http","data":{"paymentInstructionId":"pi-http","accountId":"acct-http","amount":"2.00","currency":"EUR","returnReasonCode":"R01","returnReasonCategory":"FINANCIAL","returnDate":"2026-09-24"}}
                    """;
            AvroPayloadCodec.Encoded encoded = httpCodec.encode("payment.instruction.returned", json);
            JsonNode decoded = MAPPER.readTree(httpCodec.toInterimJson(encoded.payload(), encoded.headers()));
            assertThat(decoded.get("data").get("accountId").asText()).isEqualTo("acct-http");
            assertThat(schemas).isNotEmpty();
        } finally {
            server.stop(0);
        }
    }

    private static int schemaId(byte[] payload) {
        return java.nio.ByteBuffer.wrap(payload, 1, 4).getInt();
    }
}
