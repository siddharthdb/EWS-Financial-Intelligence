package org.ewsfi.avro;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-process stand-in for Apicurio's ccompat API. Instances that share a {@code mock://} scope see
 * the same ids, so a producer and a consumer constructed separately still agree. Not durable.
 */
final class MockSchemaRegistry implements SchemaRegistryClient {

    private static final ConcurrentHashMap<String, Scope> SCOPES = new ConcurrentHashMap<>();

    private final Scope scope;

    MockSchemaRegistry(String scopeName) {
        this.scope = SCOPES.computeIfAbsent(scopeName, ignored -> new Scope());
    }

    @Override
    public int register(String subject, String schema) {
        String key = subject + "\n" + schema;
        return scope.ids.computeIfAbsent(
                key,
                ignored -> {
                    int id = scope.next.getAndIncrement();
                    scope.schemas.put(id, schema);
                    return id;
                });
    }

    @Override
    public String getSchema(int id) {
        String schema = scope.schemas.get(id);
        if (schema == null) {
            throw new IllegalStateException("No schema registered for id " + id + " in " + scope);
        }
        return schema;
    }

    private static final class Scope {
        private final AtomicInteger next = new AtomicInteger(1);
        private final ConcurrentHashMap<String, Integer> ids = new ConcurrentHashMap<>();
        private final ConcurrentHashMap<Integer, String> schemas = new ConcurrentHashMap<>();
    }
}
