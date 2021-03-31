package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala.createTuple2TypeInformation
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKeyValueKafkaAvroDeserializationFactory

import scala.reflect._

class TupleAvroKeyValueKafkaAvroDeserializerSchemaFactory[Key: ClassTag, Value: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory) {

  override protected type K = Key
  override protected type V = Value
  override protected type O = (K, V)

  override protected def createObject(key: Key, value: Value, topic: String): (Key, Value) = {
    (key, value)
  }

  override protected def createObjectTypeInformation(keyTypeInformation: TypeInformation[Key], valueTypeInformation: TypeInformation[Value]): TypeInformation[(Key, Value)] =
    createTuple2TypeInformation(keyTypeInformation, valueTypeInformation)

}
