package pl.touk.nussknacker.engine.kafka.serialization

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.serialization.DeserializationSchema
import org.apache.flink.streaming.connectors.kafka.internals.KafkaDeserializationSchemaWrapper
import org.apache.kafka.clients.consumer.ConsumerRecord

// This class exists because KafkaDeserializationSchemaWrapper throws exception for deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]])
// and for Flink's compatibility reasons we don't want to invoke deserialize(ConsumerRecord<byte[], byte[]> message, Collector<T> out)
// because it wasn't available in previous Flink's versions
@SerialVersionUID(7952681455584002102L)
@silent("deprecated")
class NKKafkaDeserializationSchemaWrapper[T](deserializationSchema: DeserializationSchema[T])
    extends KafkaDeserializationSchemaWrapper[T](deserializationSchema) {

  override def deserialize(record: ConsumerRecord[Array[Byte], Array[Byte]]): T = {
    deserializationSchema.deserialize(record.value())
  }

}
