package pl.touk.nussknacker.engine.kafka.generic

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner
import pl.touk.nussknacker.engine.kafka.generic.KafkaTypedSourceFactory.TypedJson
import pl.touk.nussknacker.engine.util.TimestampUtils.supportedTypeToMillis

object TypedJsonTimestampFieldAssigner {

  def apply(fieldName: String): SerializableTimestampAssigner[TypedJson] =
    new SerializableTimestampAssigner[TypedJson] {

      override def extractTimestamp(element: TypedJson, recordTimestamp: Long): Long =
        Option(element.value().get(fieldName))
          .map(value => supportedTypeToMillis(value, fieldName))
          .getOrElse(0L) // explicit null to 0L conversion (instead of implicit unboxing)

    }

}
