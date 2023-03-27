package pl.touk.nussknacker.engine.schemedkafka.schemaregistry;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;

import java.util.Objects;

public class GenericRecordWithSchemaId extends GenericData.Record {

    private final SchemaId schemaId;

    public GenericRecordWithSchemaId(Schema schema, SchemaId schemaId) {
        super(schema);
        this.schemaId = schemaId;
    }

    public GenericRecordWithSchemaId(GenericData.Record other, SchemaId schemaId, boolean deepCopy) {
        super(other, deepCopy);
        this.schemaId = schemaId;
    }

    public GenericRecordWithSchemaId(GenericRecordWithSchemaId other, boolean deepCopy) {
        super(other, deepCopy);
        this.schemaId = other.schemaId;
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
            return Objects.equals(schemaId, that.schemaId);
        } else {
            return true;
        }
    }

}
