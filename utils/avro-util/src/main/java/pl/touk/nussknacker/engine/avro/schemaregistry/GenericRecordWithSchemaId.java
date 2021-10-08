package pl.touk.nussknacker.engine.avro.schemaregistry;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericData;

import java.util.Objects;

public class GenericRecordWithSchemaId extends GenericData.Record {

    private final int schemaId;

    public GenericRecordWithSchemaId(Schema schema, int schemaId) {
        super(schema);
        if (schemaId < 0)
            throw new IllegalArgumentException("schemaId must be greater than or equal to zero");
        this.schemaId = schemaId;
    }

    public GenericRecordWithSchemaId(GenericData.Record other, int schemaId, boolean deepCopy) {
        super(other, deepCopy);
        if (schemaId < 0)
            throw new IllegalArgumentException("schemaId must be greater than or equal to zero");
        this.schemaId = schemaId;
    }

    public GenericRecordWithSchemaId(GenericRecordWithSchemaId other, boolean deepCopy) {
        super(other, deepCopy);
        this.schemaId = other.schemaId;
    }

    public Integer getSchemaId() {
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
