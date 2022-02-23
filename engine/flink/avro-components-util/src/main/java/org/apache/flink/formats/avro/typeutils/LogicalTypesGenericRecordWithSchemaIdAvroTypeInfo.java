package org.apache.flink.formats.avro.typeutils;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeutils.TypeSerializer;

// TODO: This class is not used now, but should be used in our TypeInformation mechanisms (for messages passed between operators and for managed stated)
public class LogicalTypesGenericRecordWithSchemaIdAvroTypeInfo extends GenericRecordAvroTypeInfo {

    private static final long serialVersionUID = -7537223822963399655L;

    private transient Schema schema;

    private int schemaId;

    public LogicalTypesGenericRecordWithSchemaIdAvroTypeInfo(Schema schema, int schemaId) {
        super(schema);
        this.schema = schema;
        this.schemaId = schemaId;
    }

    @Override
    public TypeSerializer<GenericRecord> createSerializer(ExecutionConfig config) {
        return new LogicalTypesSchemaIdAvroSerializer<>(GenericRecord.class, schema, schemaId);
    }

}
