package pl.touk.nussknacker.engine.avro.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala.createTuple2TypeInformation
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.ConfluentSchemaRegistryClientFactory
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.ConfluentKeyValueKafkaAvroDeserializationFactory

import scala.reflect._

class TupleAvroKeyValueKafkaAvroDeserializerSchemaFactory[Key: ClassTag, Value: ClassTag](schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory)
  extends ConfluentKeyValueKafkaAvroDeserializationFactory(schemaRegistryClientFactory) {

  override protected type K = Key
  override protected type V = Value
  override protected type O = (K, V)

  override protected def createObject(record: ConsumerRecord[K, V]): O = {
    (record.key(), record.value())
  }

  override protected def createObjectTypeInformation(keyTypeInformation: TypeInformation[Key], valueTypeInformation: TypeInformation[Value]): TypeInformation[O] =
    createTuple2TypeInformation(keyTypeInformation, valueTypeInformation)

}
