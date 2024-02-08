package pl.touk.nussknacker.engine.schemedkafka.source.flink

import org.apache.avro.generic.GenericRecord
import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner
import org.apache.kafka.clients.consumer.ConsumerRecord
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.VariableConstants.InputVariableName
import pl.touk.nussknacker.engine.kafka.generic.GenericTimestampAssigner
import pl.touk.nussknacker.engine.util.TimestampUtils.supportedTypeToMillis

case class UniversalTimestampFieldAssigner(fieldName: String) extends GenericTimestampAssigner {

  override def conumerRecordAssigner[K, V]: SerializableTimestampAssigner[ConsumerRecord[K, V]] =
    new SerializableTimestampAssigner[ConsumerRecord[K, V]] {

      override def extractTimestamp(element: ConsumerRecord[K, V], recordTimestamp: Long): Long =
        calculateTimestamp(Option(element.value()), fieldName)

    }

  override def contextAssigner: SerializableTimestampAssigner[Context] =
    new SerializableTimestampAssigner[Context] {

      override def extractTimestamp(element: Context, recordTimestamp: Long): Long =
        calculateTimestamp(element.get[Any](InputVariableName), fieldName)

    }

  private def calculateTimestamp[A](ctx: Option[A], fieldName: String) =
    ctx
      .flatMap {
        case genericRecord: GenericRecord                    => Option(genericRecord.get(fieldName))
        case typedMap: java.util.Map[String, Any] @unchecked => Option(typedMap.get(fieldName))
      }
      .map(v => supportedTypeToMillis(v, fieldName))
      .getOrElse(0L)

}
