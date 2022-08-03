package org.apache.flink.formats.avro.typeutils;

import org.apache.avro.Schema;
import org.apache.avro.generic.GenericRecord;
import org.apache.flink.api.common.ExecutionConfig;
import org.apache.flink.api.common.typeutils.TypeSerializer;

// TODO: This class is not used now, but should be used in our TypeInformation mechanisms (for messages passed between operators and for managed stated)
// We extend GenericRecordAvroTypeInfo and copy used hierarchy because Flink doens't support Avro logical types (https://issues.apache.org/jira/browse/FLINK-17478)
public class LogicalTypesGenericRecordAvroTypeInfo extends GenericRecordAvroTypeInfo {

    private static final long serialVersionUID = -5536249391255853626L;

    private transient Schema schema;

    public LogicalTypesGenericRecordAvroTypeInfo(Schema schema) {
        super(schema);
        this.schema = schema;
    }

    @Override
    public TypeSerializer<GenericRecord> createSerializer(ExecutionConfig config) {
        return new LogicalTypesAvroSerializer<>(GenericRecord.class, schema);
    }

}
