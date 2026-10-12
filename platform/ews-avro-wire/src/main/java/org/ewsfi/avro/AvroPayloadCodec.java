package org.ewsfi.avro;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import org.apache.avro.Conversions;
import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;
import org.apache.avro.generic.GenericDatumReader;
import org.apache.avro.generic.GenericDatumWriter;
import org.apache.avro.generic.GenericRecord;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.header.internals.RecordHeaders;
import org.ewsfi.events.decision.v1.SignalDispositionRecordedV1;
import org.ewsfi.events.facility.v1.FacilityLimitChangedV1;
import org.ewsfi.events.facility.v1.FacilityOutstandingChangedV1;
import org.ewsfi.events.feature.v1.FeatureValueUpdatedV1;
import org.ewsfi.events.payment.v1.PaymentInstructionReturnedV1;
import org.ewsfi.events.repayment.v1.ObligationDpdChangedV1;
import org.ewsfi.events.signal.v1.SignalDetectedV1;
import org.ewsfi.events.workflow.v1.CaseAssignedV1;
import org.ewsfi.events.workflow.v1.CaseClosedV1;
import org.ewsfi.events.workflow.v1.CaseEscalatedV1;
import org.ewsfi.events.workflow.v1.CaseOpenedV1;

/**
 * Encodes the interim JSON documents the Phase-1 services already build into the governed Avro
 * payload for that event type, framed with the Confluent wire format (magic byte, schema id, Avro
 * binary). Fields the governed record does not carry — event time, feature windows, signal
 * lifecycle timestamps, and the prior DPD used to suppress duplicate worsening — travel in
 * {@code ews.*} headers rather than being invented as extra Avro fields.
 *
 * <p>Where a signal or feature is still keyed by an account or facility, {@code counterpartyId} on
 * the Avro record is that same entity id. Repartition to a real counterparty id is still open.
 */
public final class AvroPayloadCodec {

    public static final String TEST_REGISTRY = "mock://ews-phase1";
    public static final String EVENT_ID = "ews.event-id";
    public static final String EVENT_TIME = "ews.event-time";
    public static final String ENTITY_TYPE = "ews.entity-type";
    public static final String PRIOR_VALUE = "ews.prior-value";
    public static final String KNOWLEDGE_TIME = "ews.knowledge-time";
    public static final String CALCULATED_AT = "ews.calculated-at";
    public static final String WINDOW_START = "ews.window-start";
    public static final String WINDOW_END = "ews.window-end";
    public static final String LINEAGE_VERSION = "ews.lineage-version";
    public static final String DETECTED_AT = "ews.detected-at";
    public static final String EFFECTIVE_AT = "ews.effective-at";
    public static final String STATUS = "ews.status";
    public static final String DATA_QUALITY = "ews.data-quality";
    public static final String DECIDED_AT = "ews.decided-at";

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final byte MAGIC = 0x0;
    private static final GenericData DATA = genericData();

    private final SchemaRegistryClient registry;

    public AvroPayloadCodec(String registryUrl) {
        if (registryUrl != null && registryUrl.startsWith("mock://")) {
            this.registry = new MockSchemaRegistry(registryUrl);
        } else {
            this.registry = new HttpSchemaRegistry(registryUrl);
        }
    }

    public static AvroPayloadCodec forTests() {
        return new AvroPayloadCodec(TEST_REGISTRY);
    }

    public Encoded encode(String eventType, String json) {
        try {
            JsonNode root = MAPPER.readTree(json);
            RecordHeaders headers = new RecordHeaders();
            GenericRecord record = toRecord(eventType, root, headers);
            byte[] payload = frame(record);
            return new Encoded(payload, headers);
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not encode " + eventType + " as Avro", e);
        }
    }

    public String toInterimJson(byte[] payload, Headers headers) {
        GenericRecord record = read(payload);
        String fullName = record.getSchema().getFullName();
        try {
            return switch (fullName) {
                case "org.ewsfi.events.payment.v1.PaymentInstructionReturnedV1" -> paymentJson(record, headers);
                case "org.ewsfi.events.repayment.v1.ObligationDpdChangedV1" -> dpdJson(record, headers);
                case "org.ewsfi.events.feature.v1.FeatureValueUpdatedV1" -> featureJson(record, headers);
                case "org.ewsfi.events.signal.v1.SignalDetectedV1" -> signalJson(record, headers);
                case "org.ewsfi.events.decision.v1.SignalDispositionRecordedV1" -> dispositionJson(record, headers);
                case "org.ewsfi.events.facility.v1.FacilityLimitChangedV1" -> facilityLimitJson(record, headers);
                case "org.ewsfi.events.facility.v1.FacilityOutstandingChangedV1" -> facilityOutstandingJson(record, headers);
                case "org.ewsfi.events.workflow.v1.CaseOpenedV1" -> caseOpenedJson(record);
                case "org.ewsfi.events.workflow.v1.CaseAssignedV1" -> caseAssignedJson(record);
                case "org.ewsfi.events.workflow.v1.CaseEscalatedV1" -> caseEscalatedJson(record);
                case "org.ewsfi.events.workflow.v1.CaseClosedV1" -> caseClosedJson(record);
                default -> throw new IllegalArgumentException("No interim mapping for " + fullName);
            };
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not decode " + fullName, e);
        }
    }

    private GenericRecord toRecord(String eventType, JsonNode root, RecordHeaders headers) {
        return switch (eventType) {
            case "payment.instruction.returned" -> paymentRecord(root, headers);
            case "obligation.dpd.changed" -> dpdRecord(root, headers);
            case "feature.value.updated" -> featureRecord(root, headers);
            case "signal.detected" -> signalRecord(root, headers);
            case "signal.disposition.recorded" -> dispositionRecord(root, headers);
            case "facility.limit.changed" -> facilityLimitRecord(root, headers);
            case "facility.outstanding.changed" -> facilityOutstandingRecord(root, headers);
            case "case.opened" -> caseOpenedRecord(root);
            case "case.assigned" -> caseAssignedRecord(root);
            case "case.escalated" -> caseEscalatedRecord(root);
            case "case.closed" -> caseClosedRecord(root);
            default -> throw new IllegalArgumentException("No Avro payload mapping for event type " + eventType);
        };
    }

    private GenericRecord paymentRecord(JsonNode root, RecordHeaders headers) {
        JsonNode data = data(root);
        rememberEnvelope(root, headers);
        Schema schema = PaymentInstructionReturnedV1.getClassSchema();
        GenericRecord record = new GenericData.Record(schema);
        record.put("paymentInstructionId", required(data, "paymentInstructionId"));
        record.put("accountId", required(data, "accountId"));
        record.put("counterpartyId", textOrNull(data, "counterpartyId"));
        record.put("facilityId", textOrNull(data, "facilityId"));
        record.put("amount", new BigDecimal(required(data, "amount")));
        record.put("currency", required(data, "currency"));
        record.put("returnReasonCode", required(data, "returnReasonCode"));
        record.put("returnReasonCategory", enumSymbol(schema, "returnReasonCategory", required(data, "returnReasonCategory")));
        record.put("returnDate", LocalDate.parse(required(data, "returnDate")));
        return record;
    }

    private GenericRecord dpdRecord(JsonNode root, RecordHeaders headers) {
        JsonNode data = data(root);
        rememberEnvelope(root, headers);
        Schema schema = ObligationDpdChangedV1.getClassSchema();
        GenericRecord record = new GenericData.Record(schema);
        record.put("facilityId", required(data, "facilityId"));
        record.put("counterpartyId", required(data, "counterpartyId"));
        record.put("obligationId", textOrNull(data, "obligationId"));
        if (data.hasNonNull("previousDpd")) {
            record.put("previousDpd", data.get("previousDpd").asInt());
        }
        record.put("currentDpd", data.get("currentDpd").asInt());
        if (data.hasNonNull("earliestUnpaidDueDate")) {
            record.put("earliestUnpaidDueDate", LocalDate.parse(data.get("earliestUnpaidDueDate").asText()));
        }
        record.put("asOfDate", LocalDate.parse(required(data, "asOfDate")));
        record.put("calculationPolicyRef", required(data, "calculationPolicyRef"));
        record.put("sourceScheduleVersion", textOrNull(data, "sourceScheduleVersion"));
        record.put("reconciled", data.path("reconciled").asBoolean(false));
        return record;
    }

    private GenericRecord featureRecord(JsonNode root, RecordHeaders headers) {
        Schema schema = FeatureValueUpdatedV1.getClassSchema();
        GenericRecord record = new GenericData.Record(schema);
        String featureValueId = required(root, "featureValueId");
        record.put("featureValueId", featureValueId);
        record.put("featureName", required(root, "featureName"));
        record.put("featureDefinitionId", required(root, "definitionId"));
        record.put("featureDefinitionVersion", required(root, "definitionVersion"));
        record.put("semanticScope", enumSymbol(schema, "semanticScope", "GLOBAL_CORE"));
        record.put("entityType", required(root, "entityType"));
        record.put("entityId", required(root, "entityId"));
        record.put("valueState", enumSymbol(schema, "valueState", required(root, "state")));
        record.put("valueType", enumSymbol(schema, "valueType", required(root, "valueType")));
        if (root.hasNonNull("valueNumeric")) {
            String numeric = root.get("valueNumeric").asText();
            if ("DECIMAL".equals(root.get("valueType").asText())) {
                record.put("decimalValue", new BigDecimal(numeric));
            } else {
                record.put("integerValue", Long.parseLong(numeric));
            }
        }
        record.put("qualityState", enumSymbol(schema, "qualityState", required(root, "qualityState")));
        record.put("revision", 1L);
        record.put("snapshotRef", featureValueId);
        header(headers, KNOWLEDGE_TIME, textOrNull(root, "knowledgeTime"));
        header(headers, CALCULATED_AT, textOrNull(root, "calculatedAt"));
        header(headers, WINDOW_START, textOrNull(root, "windowStart"));
        header(headers, WINDOW_END, textOrNull(root, "windowEnd"));
        header(headers, LINEAGE_VERSION, textOrNull(root, "lineageTransformationVersion"));
        header(headers, PRIOR_VALUE, textOrNull(root, "priorValueNumeric"));
        return record;
    }

    private GenericRecord signalRecord(JsonNode root, RecordHeaders headers) {
        Schema schema = SignalDetectedV1.getClassSchema();
        GenericRecord record = new GenericData.Record(schema);
        String signalId = required(root, "signalId");
        String entityId = required(root, "entityId");
        record.put("signalId", signalId);
        record.put("signalType", required(root, "signalType"));
        record.put("semanticScope", enumSymbol(schema, "semanticScope", required(root, "semanticScope")));
        record.put("counterpartyId", entityId);
        record.put("scopeEntityType", required(root, "entityType"));
        record.put("scopeEntityId", entityId);
        record.put("policyId", required(root, "policyId"));
        record.put("policyVersion", required(root, "policyVersion"));
        record.put("severity", enumSymbol(schema, "severity", required(root, "severity")));
        record.put("confidence", root.get("confidenceValue").asDouble());
        record.put("materiality", enumSymbol(schema, "materiality", required(root, "materialityBand")));
        List<String> evidence = texts(root.get("evidenceIds"));
        record.put("featureSnapshotIds", evidence);
        record.put("evidenceIds", evidence);
        record.put("requiresHumanValidation", true);
        record.put("signalSnapshotRef", signalId);
        header(headers, DETECTED_AT, textOrNull(root, "detectedAt"));
        header(headers, EFFECTIVE_AT, textOrNull(root, "effectiveAt"));
        header(headers, KNOWLEDGE_TIME, textOrNull(root, "knowledgeTime"));
        header(headers, STATUS, textOrNull(root, "status"));
        header(headers, DATA_QUALITY, textOrNull(root, "dataQualityState"));
        return record;
    }

    private GenericRecord dispositionRecord(JsonNode root, RecordHeaders headers) {
        Schema schema = SignalDispositionRecordedV1.getClassSchema();
        GenericRecord record = new GenericData.Record(schema);
        String signalId = required(root, "signalId");
        record.put("decisionId", required(root, "decisionId"));
        record.put("signalId", signalId);
        record.put("counterpartyId", required(root, "entityId"));
        record.put("decision", enumSymbol(schema, "decision", normalizeDecision(required(root, "decision"))));
        record.put("reasonCode", required(root, "reasonCode"));
        record.put("reasonText", textOrNull(root, "reasonText"));
        record.put("actorId", required(root, "actorId"));
        record.put("actorRole", required(root, "actorRole"));
        record.put("previousSignalSnapshotRef", signalId);
        header(headers, DECIDED_AT, textOrNull(root, "decidedAt"));
        return record;
    }

    private GenericRecord facilityLimitRecord(JsonNode root, RecordHeaders headers) {
        JsonNode data = data(root);
        rememberEnvelope(root, headers);
        Schema schema = FacilityLimitChangedV1.getClassSchema();
        GenericRecord record = new GenericData.Record(schema);
        record.put("facilityId", required(data, "facilityId"));
        record.put("counterpartyId", required(data, "counterpartyId"));
        record.put("sanctionedLimit", new BigDecimal(required(data, "sanctionedLimit")));
        record.put("currency", required(data, "currency"));
        String rule = textOrNull(data, "capacityRule");
        record.put("capacityRule", enumSymbol(schema, "capacityRule", rule == null ? "SANCTIONED_LIMIT" : rule));
        return record;
    }

    private GenericRecord facilityOutstandingRecord(JsonNode root, RecordHeaders headers) {
        JsonNode data = data(root);
        rememberEnvelope(root, headers);
        Schema schema = FacilityOutstandingChangedV1.getClassSchema();
        GenericRecord record = new GenericData.Record(schema);
        record.put("facilityId", required(data, "facilityId"));
        record.put("counterpartyId", required(data, "counterpartyId"));
        record.put("eligibleOutstanding", new BigDecimal(required(data, "eligibleOutstanding")));
        record.put("currency", required(data, "currency"));
        return record;
    }

    private GenericRecord caseOpenedRecord(JsonNode root) {
        Schema schema = CaseOpenedV1.getClassSchema();
        GenericRecord record = new GenericData.Record(schema);
        record.put("caseId", required(root, "caseId"));
        record.put("signalId", required(root, "signalId"));
        record.put("entityType", required(root, "entityType"));
        record.put("entityId", required(root, "entityId"));
        record.put("actorId", required(root, "actorId"));
        record.put("actorRole", required(root, "actorRole"));
        record.put("reason", textOrNull(root, "reason"));
        return record;
    }

    private GenericRecord caseAssignedRecord(JsonNode root) {
        Schema schema = CaseAssignedV1.getClassSchema();
        GenericRecord record = new GenericData.Record(schema);
        record.put("caseId", required(root, "caseId"));
        record.put("assigneeId", required(root, "assigneeId"));
        record.put("actorId", required(root, "actorId"));
        record.put("actorRole", required(root, "actorRole"));
        return record;
    }

    private GenericRecord caseEscalatedRecord(JsonNode root) {
        Schema schema = CaseEscalatedV1.getClassSchema();
        GenericRecord record = new GenericData.Record(schema);
        record.put("caseId", required(root, "caseId"));
        record.put("actorId", required(root, "actorId"));
        record.put("actorRole", required(root, "actorRole"));
        record.put("reason", textOrNull(root, "reason"));
        return record;
    }

    private GenericRecord caseClosedRecord(JsonNode root) {
        Schema schema = CaseClosedV1.getClassSchema();
        GenericRecord record = new GenericData.Record(schema);
        record.put("caseId", required(root, "caseId"));
        record.put("actorId", required(root, "actorId"));
        record.put("actorRole", required(root, "actorRole"));
        record.put("outcome", required(root, "outcome"));
        return record;
    }

    private String facilityLimitJson(GenericRecord record, Headers headers) throws Exception {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("facilityId", text(record.get("facilityId")));
        data.put("counterpartyId", text(record.get("counterpartyId")));
        data.put("sanctionedLimit", decimalText(record.get("sanctionedLimit"), 20, 2));
        data.put("currency", text(record.get("currency")));
        data.put("capacityRule", text(record.get("capacityRule")));
        return envelopeJson("facility.limit.changed", "FACILITY", text(record.get("facilityId")), data, headers);
    }

    private String facilityOutstandingJson(GenericRecord record, Headers headers) throws Exception {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("facilityId", text(record.get("facilityId")));
        data.put("counterpartyId", text(record.get("counterpartyId")));
        data.put("eligibleOutstanding", decimalText(record.get("eligibleOutstanding"), 20, 2));
        data.put("currency", text(record.get("currency")));
        return envelopeJson(
                "facility.outstanding.changed", "FACILITY", text(record.get("facilityId")), data, headers);
    }

    private String caseOpenedJson(GenericRecord record) throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("caseId", text(record.get("caseId")));
        node.put("signalId", text(record.get("signalId")));
        node.put("entityType", text(record.get("entityType")));
        node.put("entityId", text(record.get("entityId")));
        node.put("actorId", text(record.get("actorId")));
        node.put("actorRole", text(record.get("actorRole")));
        node.put("reason", text(record.get("reason")));
        return MAPPER.writeValueAsString(node);
    }

    private String caseAssignedJson(GenericRecord record) throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("caseId", text(record.get("caseId")));
        node.put("assigneeId", text(record.get("assigneeId")));
        node.put("actorId", text(record.get("actorId")));
        node.put("actorRole", text(record.get("actorRole")));
        return MAPPER.writeValueAsString(node);
    }

    private String caseEscalatedJson(GenericRecord record) throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("caseId", text(record.get("caseId")));
        node.put("actorId", text(record.get("actorId")));
        node.put("actorRole", text(record.get("actorRole")));
        node.put("reason", text(record.get("reason")));
        return MAPPER.writeValueAsString(node);
    }

    private String caseClosedJson(GenericRecord record) throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("caseId", text(record.get("caseId")));
        node.put("actorId", text(record.get("actorId")));
        node.put("actorRole", text(record.get("actorRole")));
        node.put("outcome", text(record.get("outcome")));
        return MAPPER.writeValueAsString(node);
    }

    private String paymentJson(GenericRecord record, Headers headers) throws Exception {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("paymentInstructionId", text(record.get("paymentInstructionId")));
        data.put("accountId", text(record.get("accountId")));
        data.put("amount", decimalText(record.get("amount"), 20, 2));
        data.put("currency", text(record.get("currency")));
        data.put("returnReasonCode", text(record.get("returnReasonCode")));
        data.put("returnReasonCategory", text(record.get("returnReasonCategory")));
        data.put("returnDate", dateText(record.get("returnDate")));
        return envelopeJson("payment.instruction.returned", "ACCOUNT", text(record.get("accountId")), data, headers);
    }

    private String dpdJson(GenericRecord record, Headers headers) throws Exception {
        ObjectNode data = MAPPER.createObjectNode();
        data.put("facilityId", text(record.get("facilityId")));
        data.put("counterpartyId", text(record.get("counterpartyId")));
        if (record.get("obligationId") != null) {
            data.put("obligationId", text(record.get("obligationId")));
        }
        if (record.get("previousDpd") != null) {
            data.put("previousDpd", ((Number) record.get("previousDpd")).intValue());
        } else {
            data.putNull("previousDpd");
        }
        data.put("currentDpd", ((Number) record.get("currentDpd")).intValue());
        if (record.get("earliestUnpaidDueDate") != null) {
            data.put("earliestUnpaidDueDate", dateText(record.get("earliestUnpaidDueDate")));
        }
        data.put("asOfDate", dateText(record.get("asOfDate")));
        data.put("calculationPolicyRef", text(record.get("calculationPolicyRef")));
        if (record.get("sourceScheduleVersion") != null) {
            data.put("sourceScheduleVersion", text(record.get("sourceScheduleVersion")));
        }
        data.put("reconciled", Boolean.TRUE.equals(record.get("reconciled")));
        return envelopeJson("obligation.dpd.changed", "FACILITY", text(record.get("facilityId")), data, headers);
    }

    private String featureJson(GenericRecord record, Headers headers) throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("featureValueId", text(record.get("featureValueId")));
        node.put("definitionId", text(record.get("featureDefinitionId")));
        node.put("featureName", text(record.get("featureName")));
        node.put("definitionVersion", text(record.get("featureDefinitionVersion")));
        node.put("entityType", text(record.get("entityType")));
        node.put("entityId", text(record.get("entityId")));
        node.put("state", text(record.get("valueState")));
        node.put("valueType", text(record.get("valueType")));
        if (record.get("decimalValue") instanceof BigDecimal decimal) {
            node.put("valueNumeric", decimal.stripTrailingZeros().toPlainString());
        } else if (record.get("integerValue") != null) {
            node.put("valueNumeric", Long.toString(((Number) record.get("integerValue")).longValue()));
        } else {
            node.putNull("valueNumeric");
        }
        node.put("knowledgeTime", headerText(headers, KNOWLEDGE_TIME));
        node.put("calculatedAt", headerText(headers, CALCULATED_AT));
        node.put("windowStart", headerText(headers, WINDOW_START));
        node.put("windowEnd", headerText(headers, WINDOW_END));
        node.put("qualityState", text(record.get("qualityState")));
        node.put("lineageTransformationVersion", headerText(headers, LINEAGE_VERSION));
        String prior = headerText(headers, PRIOR_VALUE);
        if (prior != null) {
            node.put("priorValueNumeric", prior);
        }
        return MAPPER.writeValueAsString(node);
    }

    private String signalJson(GenericRecord record, Headers headers) throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("signalId", text(record.get("signalId")));
        node.put("signalType", text(record.get("signalType")));
        node.put("semanticScope", text(record.get("semanticScope")));
        node.put("entityType", text(record.get("scopeEntityType")));
        node.put("entityId", text(record.get("scopeEntityId")));
        node.put("status", headerText(headers, STATUS) == null ? "PROPOSED" : headerText(headers, STATUS));
        node.put("severity", text(record.get("severity")));
        node.put("confidenceValue", ((Number) record.get("confidence")).doubleValue());
        node.put("materialityBand", text(record.get("materiality")));
        node.put("detectedAt", headerText(headers, DETECTED_AT));
        node.put("effectiveAt", headerText(headers, EFFECTIVE_AT));
        node.put("knowledgeTime", headerText(headers, KNOWLEDGE_TIME));
        node.put("policyId", text(record.get("policyId")));
        node.put("policyVersion", text(record.get("policyVersion")));
        node.put("dataQualityState", headerText(headers, DATA_QUALITY));
        node.set("evidenceIds", MAPPER.valueToTree(stringList(record.get("evidenceIds"))));
        return MAPPER.writeValueAsString(node);
    }

    private String dispositionJson(GenericRecord record, Headers headers) throws Exception {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("decisionId", text(record.get("decisionId")));
        node.put("signalId", text(record.get("signalId")));
        node.put("entityId", text(record.get("counterpartyId")));
        node.put("decision", text(record.get("decision")));
        node.put("reasonCode", text(record.get("reasonCode")));
        node.put("reasonText", text(record.get("reasonText")));
        node.put("actorId", text(record.get("actorId")));
        node.put("actorRole", text(record.get("actorRole")));
        node.put("decidedAt", headerText(headers, DECIDED_AT));
        return MAPPER.writeValueAsString(node);
    }

    private String envelopeJson(String eventType, String defaultEntityType, String entityId, ObjectNode data, Headers headers)
            throws Exception {
        ObjectNode envelope = MAPPER.createObjectNode();
        envelope.put("eventId", headerText(headers, EVENT_ID));
        envelope.put("eventType", eventType);
        envelope.put("eventTime", headerText(headers, EVENT_TIME));
        envelope.put("entityType", headerText(headers, ENTITY_TYPE) == null ? defaultEntityType : headerText(headers, ENTITY_TYPE));
        envelope.put("entityId", entityId);
        envelope.set("data", data);
        return MAPPER.writeValueAsString(envelope);
    }

    private byte[] frame(GenericRecord record) throws Exception {
        Schema schema = record.getSchema();
        fillEmptyArrays(record);
        int schemaId = registry.register(schema.getFullName(), schema.toString());
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(body, null);
        new GenericDatumWriter<GenericRecord>(schema, DATA).write(record, encoder);
        encoder.flush();
        byte[] avro = body.toByteArray();
        ByteBuffer framed = ByteBuffer.allocate(5 + avro.length);
        framed.put(MAGIC);
        framed.putInt(schemaId);
        framed.put(avro);
        return framed.array();
    }

    private GenericRecord read(byte[] payload) {
        if (payload == null || payload.length < 5 || payload[0] != MAGIC) {
            throw new IllegalArgumentException("Kafka value is not a Confluent Avro frame");
        }
        int schemaId = ByteBuffer.wrap(payload, 1, 4).getInt();
        Schema schema = new Schema.Parser().parse(registry.getSchema(schemaId));
        try {
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(payload, 5, payload.length - 5, null);
            return new GenericDatumReader<GenericRecord>(schema, schema, DATA).read(null, decoder);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not read Avro payload for schema id " + schemaId, e);
        }
    }

    private static void fillEmptyArrays(GenericRecord record) {
        for (Schema.Field field : record.getSchema().getFields()) {
            if (record.get(field.name()) == null && isArray(field.schema())) {
                record.put(field.name(), java.util.List.of());
            }
        }
    }

    private static boolean isArray(Schema schema) {
        if (schema.getType() == Schema.Type.ARRAY) {
            return true;
        }
        if (schema.getType() == Schema.Type.UNION) {
            for (Schema branch : schema.getTypes()) {
                if (branch.getType() == Schema.Type.ARRAY) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void rememberEnvelope(JsonNode root, RecordHeaders headers) {
        header(headers, EVENT_ID, textOrNull(root, "eventId"));
        header(headers, EVENT_TIME, textOrNull(root, "eventTime"));
        header(headers, ENTITY_TYPE, textOrNull(root, "entityType"));
    }

    private static JsonNode data(JsonNode root) {
        return root.has("data") ? root.get("data") : root;
    }

    private static String required(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull() || value.asText().isBlank()) {
            throw new IllegalArgumentException("Missing required field " + field);
        }
        return value.asText();
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }

    private static Object enumSymbol(Schema recordSchema, String field, String symbol) {
        Schema fieldSchema = recordSchema.getField(field).schema();
        if (fieldSchema.getType() == Schema.Type.UNION) {
            for (Schema branch : fieldSchema.getTypes()) {
                if (branch.getType() == Schema.Type.ENUM) {
                    fieldSchema = branch;
                    break;
                }
            }
        }
        return new GenericData.EnumSymbol(fieldSchema, symbol);
    }

    private static String normalizeDecision(String decision) {
        if ("ACCEPTED".equals(decision)) {
            return "ACCEPT";
        }
        if ("REJECTED".equals(decision)) {
            return "REJECT";
        }
        return decision;
    }

    private static List<String> texts(JsonNode node) {
        List<String> values = new ArrayList<>();
        if (node != null && node.isArray()) {
            node.forEach(item -> values.add(item.asText()));
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    private static List<String> stringList(Object raw) {
        List<String> values = new ArrayList<>();
        if (raw instanceof List<?> list) {
            for (Object item : list) {
                values.add(text(item));
            }
        }
        return values;
    }

    private static String decimalText(Object raw, int precision, int scale) {
        if (raw instanceof BigDecimal decimal) {
            return decimal.setScale(scale).toPlainString();
        }
        throw new IllegalArgumentException("Expected a decimal amount but was " + raw);
    }

    private static String dateText(Object raw) {
        if (raw instanceof LocalDate date) {
            return date.toString();
        }
        if (raw instanceof Number number) {
            return LocalDate.ofEpochDay(number.longValue()).toString();
        }
        return text(raw);
    }

    private static String text(Object raw) {
        return raw == null ? null : raw.toString();
    }

    private static void header(RecordHeaders headers, String key, String value) {
        if (value != null) {
            headers.add(key, value.getBytes(StandardCharsets.UTF_8));
        }
    }

    private static String headerText(Headers headers, String key) {
        if (headers == null || headers.lastHeader(key) == null) {
            return null;
        }
        return new String(headers.lastHeader(key).value(), StandardCharsets.UTF_8);
    }

    private static GenericData genericData() {
        GenericData data = new GenericData();
        data.addLogicalTypeConversion(new Conversions.DecimalConversion());
        data.addLogicalTypeConversion(new org.apache.avro.data.TimeConversions.DateConversion());
        return data;
    }

    public record Encoded(byte[] payload, Headers headers) {}
}
