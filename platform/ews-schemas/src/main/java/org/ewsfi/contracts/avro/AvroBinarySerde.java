package org.ewsfi.contracts.avro;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import org.apache.avro.io.BinaryDecoder;
import org.apache.avro.io.BinaryEncoder;
import org.apache.avro.io.DecoderFactory;
import org.apache.avro.io.EncoderFactory;
import org.apache.avro.specific.SpecificDatumReader;
import org.apache.avro.specific.SpecificDatumWriter;
import org.apache.avro.specific.SpecificRecordBase;

/**
 * Encodes/decodes a generated Avro {@link SpecificRecordBase} to/from its Avro single-object
 * binary wire representation, using the record's own compiled {@code SCHEMA$} as both writer and
 * reader schema.
 *
 * <p>Roadmap item 1.17 (docs/architecture/08-roadmap-progress-tracker.md) calls for switching the
 * Kafka wire format from JSON to "Avro + Schema Registry" per docs/architecture/03-event-architecture.md
 * Section 10 and ADR-011 (Apicurio Registry). This class implements the Avro binary codec half of
 * that -- real, tested Avro binary serialization replacing the interim JSON wire format -- without
 * a live Schema Registry, since Apicurio (per ADR-011, via {@code docker-compose.yml}) requires a
 * Docker daemon that isn't available in this build/dev environment. Because both producer and
 * consumer here are the same compiled service artifacts sharing this module's generated classes,
 * schema agreement is enforced at compile time rather than resolved from a registry at runtime --
 * a deliberate, documented interim step. Wiring this codec into the outbox publisher, the Kafka
 * Streams topologies, and the {@code @KafkaListener} consumers (and migrating {@code
 * outbox_event.payload} off its current JSON-text column) is deferred future work, tracked as the
 * remainder of item 1.17, since it touches every producer/consumer pair atomically and needs its
 * own dedicated, carefully-sequenced migration pass rather than being folded into this one.
 */
public class AvroBinarySerde<T extends SpecificRecordBase> {

    private final Class<T> type;

    public AvroBinarySerde(Class<T> type) {
        this.type = type;
    }

    public byte[] serialize(T record) {
        try {
            SpecificDatumWriter<T> writer = new SpecificDatumWriter<>(type);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            BinaryEncoder encoder = EncoderFactory.get().binaryEncoder(out, null);
            writer.write(record, encoder);
            encoder.flush();
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to serialize " + type.getSimpleName() + " to Avro binary", e);
        }
    }

    public T deserialize(byte[] bytes) {
        try {
            SpecificDatumReader<T> reader = new SpecificDatumReader<>(type);
            BinaryDecoder decoder = DecoderFactory.get().binaryDecoder(bytes, null);
            return reader.read(null, decoder);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "Failed to deserialize Avro binary to " + type.getSimpleName(), e);
        }
    }
}
