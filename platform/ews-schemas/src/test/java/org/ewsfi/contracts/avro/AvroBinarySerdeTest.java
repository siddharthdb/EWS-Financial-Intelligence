package org.ewsfi.contracts.avro;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.ewsfi.events.payment.v1.PaymentInstructionReturnedV1;
import org.ewsfi.events.payment.v1.ReturnReasonCategoryV1;
import org.ewsfi.events.repayment.v1.ObligationDpdChangedV1;
import org.ewsfi.events.v1.CanonicalEventEnvelopeV1;
import org.ewsfi.events.v1.DataClassificationV1;
import org.ewsfi.events.v1.EntityReferenceV1;
import org.ewsfi.events.v1.ProducerIdentityV1;
import org.ewsfi.events.v1.SensitivityV1;
import org.ewsfi.events.v1.SourceReferenceV1;
import org.junit.jupiter.api.Test;

/**
 * Proves {@link AvroBinarySerde} round-trips real generated Avro records (not stubs) to and from
 * binary -- the Avro-binary half of roadmap item 1.17 (switching the Kafka wire format from JSON
 * to Avro). Covers the shared envelope and the two payload types the payment-return and DPD slices
 * actually publish.
 */
class AvroBinarySerdeTest {

    @Test
    void roundTripsTheCanonicalEventEnvelope() {
        AvroBinarySerde<CanonicalEventEnvelopeV1> serde = new AvroBinarySerde<>(CanonicalEventEnvelopeV1.class);

        CanonicalEventEnvelopeV1 envelope =
                CanonicalEventEnvelopeV1.newBuilder()
                        .setEventId(UUID.randomUUID())
                        .setEventType("payment.instruction.returned")
                        .setEventVersion("v1")
                        .setProducer(
                                ProducerIdentityV1.newBuilder()
                                        .setService("ews-ingestion-service")
                                        .setInstance(null)
                                        .setVersion(null)
                                        .build())
                        .setEntity(
                                EntityReferenceV1.newBuilder()
                                        .setType("ACCOUNT")
                                        .setId("acct-1")
                                        .setResolutionRef(null)
                                        .setResolutionConfidence(null)
                                        .build())
                        .setAggregateSequence(null)
                        .setPartitionKey("acct-1")
                        .setEventTime(Instant.parse("2026-09-24T12:00:00Z"))
                        .setEffectiveTime(null)
                        .setKnowledgeTime(Instant.parse("2026-09-24T12:00:00Z"))
                        .setIngestedAt(Instant.parse("2026-09-24T12:00:01Z"))
                        .setJurisdiction(null)
                        .setMarket(null)
                        .setSource(
                                SourceReferenceV1.newBuilder()
                                        .setSystem("ews-ingestion-service")
                                        .setProvider(null)
                                        .setRecordId(null)
                                        .setAuthorityTier(null)
                                        .setEvidenceIds(List.of())
                                        .setSourceRightsRef(null)
                                        .setSourcePublishedAt(null)
                                        .build())
                        .setCorrelationId(null)
                        .setCausationId(null)
                        .setTraceId(null)
                        .setDataClassification(
                                DataClassificationV1.newBuilder()
                                        .setSensitivity(SensitivityV1.INTERNAL)
                                        .setContainsPii(false)
                                        .setRetentionClass(null)
                                        .build())
                        .build();

        byte[] bytes = serde.serialize(envelope);
        CanonicalEventEnvelopeV1 roundTripped = serde.deserialize(bytes);

        assertThat(roundTripped).isEqualTo(envelope);
        assertThat(roundTripped.getEventType()).isEqualTo("payment.instruction.returned");
        assertThat(roundTripped.getEntity().getId()).isEqualTo("acct-1");
    }

    @Test
    void roundTripsAPaymentInstructionReturnedPayload() {
        AvroBinarySerde<PaymentInstructionReturnedV1> serde =
                new AvroBinarySerde<>(PaymentInstructionReturnedV1.class);

        PaymentInstructionReturnedV1 payload =
                PaymentInstructionReturnedV1.newBuilder()
                        .setPaymentInstructionId("pi-1")
                        .setAccountId("acct-1")
                        .setCounterpartyId(null)
                        .setFacilityId(null)
                        .setAmount(ByteBuffer.wrap(new byte[] {0x04, (byte) 0xD2}))
                        .setCurrency("USD")
                        .setReturnReasonCode("R01")
                        .setReturnReasonCategory(ReturnReasonCategoryV1.FINANCIAL)
                        .setInstrumentType(null)
                        .setInstructionDate(null)
                        .setReturnDate(LocalDate.parse("2026-09-24"))
                        .setSourceTransactionId(null)
                        .setReversalOfEventId(null)
                        .build();

        byte[] bytes = serde.serialize(payload);
        PaymentInstructionReturnedV1 roundTripped = serde.deserialize(bytes);

        assertThat(roundTripped).isEqualTo(payload);
        assertThat(roundTripped.getReturnReasonCategory()).isEqualTo(ReturnReasonCategoryV1.FINANCIAL);
    }

    @Test
    void roundTripsAnObligationDpdChangedPayload() {
        AvroBinarySerde<ObligationDpdChangedV1> serde = new AvroBinarySerde<>(ObligationDpdChangedV1.class);

        ObligationDpdChangedV1 payload =
                ObligationDpdChangedV1.newBuilder()
                        .setFacilityId("fac-1")
                        .setCounterpartyId("cp-1")
                        .setObligationId(null)
                        .setPreviousDpd(0)
                        .setCurrentDpd(5)
                        .setEarliestUnpaidDueDate(null)
                        .setAsOfDate(LocalDate.parse("2026-09-24"))
                        .setCalculationPolicyRef("POL-DPD-CALC-001")
                        .setSourceScheduleVersion(null)
                        .setReconciled(true)
                        .build();

        byte[] bytes = serde.serialize(payload);
        ObligationDpdChangedV1 roundTripped = serde.deserialize(bytes);

        assertThat(roundTripped).isEqualTo(payload);
        assertThat(roundTripped.getCurrentDpd()).isEqualTo(5);
    }
}
