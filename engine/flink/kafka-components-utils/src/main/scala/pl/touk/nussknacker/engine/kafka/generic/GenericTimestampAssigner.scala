package pl.touk.nussknacker.engine.kafka.generic

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.Context

trait GenericTimestampAssigner {
  def conumerRecordAssigner[K, V]: SerializableTimestampAssigner[ConsumerRecord[K, V]]
  def contextAssigner: SerializableTimestampAssigner[Context]
}
