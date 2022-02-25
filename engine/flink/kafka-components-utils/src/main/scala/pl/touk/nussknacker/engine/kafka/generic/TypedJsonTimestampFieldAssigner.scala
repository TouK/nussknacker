package pl.touk.nussknacker.engine.kafka.generic

import org.apache.flink.api.common.eventtime.SerializableTimestampAssigner
import pl.touk.nussknacker.engine.kafka.generic.KafkaTypedSourceFactory.TypedJson

object TypedJsonTimestampFieldAssigner {

  def apply(fieldName: String): SerializableTimestampAssigner[TypedJson] = new SerializableTimestampAssigner[TypedJson] {
    // TODO: Handle exceptions thrown within sources (now the whole process fails)
    override def extractTimestamp(element: TypedJson, recordTimestamp: Long): Long =
      Option(element.value().get(fieldName))
        .map(_.asInstanceOf[Long])
        .getOrElse(0L) // explicit null to 0L conversion (instead of implicit unboxing)
  }

}
