package org.example.neo4j.repository;

import org.neo4j.driver.Value;

public abstract class NodeRepository {

    protected String asNullableString(Value value) {
        return value == null || value.isNull() ? null : value.asString();
    }

    protected int asNullableInt(Value value) {
        return value == null || value.isNull() ? -1 : value.asInt();
    }
}
