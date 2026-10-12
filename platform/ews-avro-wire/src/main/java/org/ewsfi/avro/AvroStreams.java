package org.ewsfi.avro;

import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.ProcessorSupplier;
import org.apache.kafka.streams.processor.api.Record;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Kafka Streams adapters around {@link AvroPayloadCodec}. Decode drops a record that is not a
 * governed Avro frame instead of failing the task. Encode attaches the interim headers the codec
 * needs on the way back to JSON.
 */
public final class AvroStreams {

    private static final Logger log = LoggerFactory.getLogger(AvroStreams.class);

    private AvroStreams() {}

    public static ProcessorSupplier<String, byte[], String, String> decode(AvroPayloadCodec codec) {
        return () -> new Processor<String, byte[], String, String>() {
            private ProcessorContext<String, String> context;

            @Override
            public void init(ProcessorContext<String, String> context) {
                this.context = context;
            }

            @Override
            public void process(Record<String, byte[]> record) {
                try {
                    String json = codec.toInterimJson(record.value(), record.headers());
                    context.forward(record.withValue(json));
                } catch (RuntimeException e) {
                    log.debug("Dropping Kafka record that is not a governed Avro payload: {}", e.getMessage());
                }
            }

            @Override
            public void close() {}
        };
    }

    public static ProcessorSupplier<String, String, String, byte[]> encode(AvroPayloadCodec codec, String eventType) {
        return () -> new Processor<String, String, String, byte[]>() {
            private ProcessorContext<String, byte[]> context;

            @Override
            public void init(ProcessorContext<String, byte[]> context) {
                this.context = context;
            }

            @Override
            public void process(Record<String, String> record) {
                AvroPayloadCodec.Encoded encoded = codec.encode(eventType, record.value());
                encoded.headers().forEach(header -> record.headers().add(header.key(), header.value()));
                context.forward(record.withValue(encoded.payload()));
            }

            @Override
            public void close() {}
        };
    }
}
