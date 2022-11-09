package pl.touk.nussknacker.engine.schemedkafka.source.flink

import org.apache.avro.generic.GenericRecord
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner
import org.apache.kafka.clients.consumer.ConsumerRecord

object UniversalTimestampFieldAssigner {

  def apply[K, V](fieldName: String): SerializableTimestampAssigner[ConsumerRecord[K, V]] = new SerializableTimestampAssigner[ConsumerRecord[K, V]] {
    override def extractTimestamp(element: ConsumerRecord[K, V], recordTimestamp: Long): Long = {
      val timestampOpt = Option(element.value() match {
        case genericRecord: GenericRecord => genericRecord.get(fieldName)
        case typedMap: java.util.Map[String, Any]@unchecked => typedMap.get(fieldName)
      })
      timestampOpt.map(_.asInstanceOf[Long]).getOrElse(0L) // explicit null to 0L conversion (instead of implicit unboxing)
    }
  }

}
