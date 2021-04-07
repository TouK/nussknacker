package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala.createTuple2TypeInformation
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKeyValueKafkaAvroDeserializationFactory

import scala.reflect._

class TupleAvroKeyValueKafkaAvroDeserializerSchemaFactory[Key: ClassTag, Value: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory) {

  override protected type O = (Key, Value)

  override protected def createObject[K: ClassTag, V: ClassTag](key: K, value: V, topic: String): (Key, Value) = {
    (key.asInstanceOf[Key], value.asInstanceOf[Value])
  }

  override protected def createObjectTypeInformation[K: ClassTag, V: ClassTag](keyTypeInformation: TypeInformation[K], valueTypeInformation: TypeInformation[V]): TypeInformation[O] =
    createTuple2TypeInformation(keyTypeInformation, valueTypeInformation)
      .asInstanceOf[TypeInformation[O]]

}
