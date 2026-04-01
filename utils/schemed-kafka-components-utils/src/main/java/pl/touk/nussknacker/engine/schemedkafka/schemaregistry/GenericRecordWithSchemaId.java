package pl.touk.nussknacker.engine.schemedkafka.schemaregistry;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;

import java.util.Objects;

public class GenericRecordWithSchemaId extends GenericData.Record {

    private final int schemaRegistryId;
    private final SchemaId schemaId;

    public GenericRecordWithSchemaId(Schema schema, int schemaRegistryId, SchemaId schemaId) {
        super(schema);
        this.schemaRegistryId = schemaRegistryId;
        this.schemaId = schemaId;
    }

    public GenericRecordWithSchemaId(GenericData.Record other, int schemaRegistryId, SchemaId schemaId, boolean deepCopy) {
        super(other, deepCopy);
        this.schemaRegistryId = schemaRegistryId;
        this.schemaId = schemaId;
    }

    public GenericRecordWithSchemaId(GenericRecordWithSchemaId other, boolean deepCopy) {
        super(other, deepCopy);
        this.schemaRegistryId = other.schemaRegistryId;
        this.schemaId = other.schemaId;
    }

    public int getSchemaRegistryId() {
        return schemaRegistryId;
    }

    public SchemaId getSchemaId() {
        return schemaId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!super.equals(o)) return false;
        if (o instanceof GenericRecordWithSchemaId) {
            GenericRecordWithSchemaId that = (GenericRecordWithSchemaId) o;
            return schemaRegistryId == that.schemaRegistryId && Objects.equals(schemaId, that.schemaId);
        } else {
            return true;
        }
    }

}
