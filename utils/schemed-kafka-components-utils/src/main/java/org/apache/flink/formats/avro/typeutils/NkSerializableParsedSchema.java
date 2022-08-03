/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.formats.avro.typeutils;

import io.confluent.kafka.schemaregistry.ParsedSchema;
import io.confluent.kafka.schemaregistry.avro.AvroSchema;
import org.apache.avro.Schema;
import org.apache.avro.Schema.Parser;
import org.apache.avro.reflect.Nullable;
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.AvroSchemaWithJsonPayload;
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.client.OpenAPIJsonSchema;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

/**
 * This is copy of Flinks class with changed visibility.
 *
 * A wrapper for Avro {@link Schema}, that is Java serializable.
 */
public final class NkSerializableParsedSchema<T extends ParsedSchema> implements Serializable {

    private static final long serialVersionUID = 2;
    private static final byte avroSchemaType = 1;
    private static final byte jsonSchemaType = 2;
    private static final byte avroSchemaWithJsonPayloadType = 3;

    private transient @Nullable T schema;

    public NkSerializableParsedSchema() {
    }

    public NkSerializableParsedSchema(T schema) {
        this.schema = schema;
    }

    public T getParsedSchema() {
        return schema;
    }

    private void writeObject(ObjectOutputStream oos) throws IOException {
        if (schema == null) {
            oos.writeBoolean(false);
        } else {
            oos.writeBoolean(true);
            if (schema instanceof AvroSchema) {
                oos.writeByte(avroSchemaType);
                oos.writeUTF(((AvroSchema) schema).rawSchema().toString(false));
            } else if (schema instanceof OpenAPIJsonSchema) {
                oos.writeByte(jsonSchemaType);
                oos.writeUTF(((OpenAPIJsonSchema) schema).rawSchema().toString());
            } else if (schema instanceof AvroSchemaWithJsonPayload) {
                oos.writeByte(avroSchemaWithJsonPayloadType);
                oos.writeUTF(((AvroSchemaWithJsonPayload) schema).rawSchema().toString());
            }
            else {
                throw new IllegalStateException("Shouldn't happen. Unsupported schema type: " + schema.schemaType());
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void readObject(ObjectInputStream ois) throws ClassNotFoundException, IOException {
        if (ois.readBoolean()) {
            byte schemaType = ois.readByte();
            switch (schemaType) {
                case avroSchemaType:
                    String avroSchemaStr = ois.readUTF();
                    this.schema = (T) new AvroSchema(new Parser().parse(avroSchemaStr));
                    break;
                case jsonSchemaType:
                    String jsonSchemaStr = ois.readUTF();
                    this.schema = (T) new OpenAPIJsonSchema(jsonSchemaStr);
                    break;
                case avroSchemaWithJsonPayloadType:
                    String avroSchema = ois.readUTF();
                    this.schema = (T) new AvroSchemaWithJsonPayload(new AvroSchema(avroSchema));
                    break;
                default:
                    throw new IllegalStateException("Shouldn't happen. Unsupported schema type: " + schemaType);
            }
        } else {
            this.schema = null;
        }
    }
}
