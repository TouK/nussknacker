package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala.createTuple2TypeInformation
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.{ConfluentKeyValueKafkaAvroDeserializationFactory, SchemaDeterminingStrategy}

class TupleAvroKeyValueKafkaAvroDeserializerSchemaFactory[Key, Value](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)(implicit keyTypInfo: TypeInformation[Key], valueTypInfo: TypeInformation[Value])
  extends ConfluentKeyValueKafkaAvroDeserializationFactory[(Key, Value)](SchemaDeterminingStrategy.FromSubjectVersion, schemaRegistryClientFactory, useSpecificAvroReader = false)(
    createTuple2TypeInformation(keyTypInfo, valueTypInfo)
  ) {

  override protected type K = Key
  override protected type V = Value

  override protected def createObject(key: Key, value: Value, topic: String): (Key, Value) = {
    (key, value)
  }
}
