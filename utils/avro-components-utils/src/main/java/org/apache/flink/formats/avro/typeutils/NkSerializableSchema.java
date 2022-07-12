package org.apache.flink.formats.avro.typeutils;

import org.apache.avro.Schema;
import org.apache.avro.reflect.Nullable;
import pl.touk.nussknacker.engine.avro.schemaregistry.AvroSchema;
import pl.touk.nussknacker.engine.avro.schemaregistry.JsonSchema;
import pl.touk.nussknacker.engine.avro.schemaregistry.SchemaContainer;
import pl.touk.nussknacker.engine.json.JsonSchemaBuilder;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

public final class NkSerializableSchema implements Serializable {
    private static final long serialVersionUID = 1;
    private transient @Nullable SchemaContainer schema;

    public NkSerializableSchema() {
    }

    public NkSerializableSchema(SchemaContainer schema) {
    }

    public SchemaContainer getSchema() {
        return schema;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        if (schema == null) {
            oos.writeBoolean(false);
        } else {
            if (schema instanceof AvroSchema) {
                oos.write(0);
                oos.writeUTF(((AvroSchema) schema).schema().toString(false));
            } else if (schema instanceof JsonSchema) {
                oos.write(1);
                oos.writeUTF(((JsonSchema) schema).schema().toString());
            } else {
                throw new IllegalStateException("not supported schema type");
            }
        }
    }

    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        if (ois.readBoolean()) {
            int type = ois.readInt();
            String schema = ois.readUTF();
            if (type == 0) {
                this.schema = new AvroSchema(new Schema.Parser().parse(schema));
            } else if (type == 1) {
                this.schema = new JsonSchema(JsonSchemaBuilder.parseSchema(schema));
            } else {
                throw new IllegalStateException("not supported schema type");
            }
        } else {
            this.schema = null;
        }
    }
}
