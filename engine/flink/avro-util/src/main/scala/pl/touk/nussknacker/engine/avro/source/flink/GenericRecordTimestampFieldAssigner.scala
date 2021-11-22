package pl.touk.nussknacker.engine.avro.source.flink

import org.apache.avro.generic.GenericRecord
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner
import org.apache.kafka.clients.consumer.ConsumerRecord

object GenericRecordTimestampFieldAssigner {

  def apply[K, V](fieldName: String): SerializableTimestampAssigner[ConsumerRecord[K, V]] = new SerializableTimestampAssigner[ConsumerRecord[K, V]] {
    // TODO: Handle exceptions thrown within sources (now the whole process fails)
    override def extractTimestamp(element: ConsumerRecord[K, V], recordTimestamp: Long): Long =
      Option(element.value().asInstanceOf[GenericRecord].get(fieldName))
        .map(_.asInstanceOf[Long])
        .getOrElse(0L) // explicit null to 0L conversion (instead of implicit unboxing)
  }

}
