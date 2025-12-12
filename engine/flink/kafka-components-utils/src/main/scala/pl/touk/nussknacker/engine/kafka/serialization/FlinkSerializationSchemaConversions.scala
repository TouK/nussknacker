package pl.touk.nussknacker.engine.kafka.serialization

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.connector.kafka.sink.KafkaRecordSerializationSchema
import pl.touk.nussknacker.engine.kafka.serialization

import java.lang

object FlinkSerializationSchemaConversions extends LazyLogging {

  def wrapToFlinkSerializationSchema[T](
      serializationSchema: serialization.KafkaSerializationSchema[T]
  ): KafkaRecordSerializationSchema[T] =
    (element: T, _: KafkaRecordSerializationSchema.KafkaSinkContext, timestamp: lang.Long) =>
      serializationSchema.serialize(element, timestamp)

}
