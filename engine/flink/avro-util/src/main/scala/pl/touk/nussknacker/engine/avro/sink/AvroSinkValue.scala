package pl.touk.nussknacker.engine.avro.sink

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.avro.sink.KafkaAvroSink.InvalidSinkValueError

object AvroSinkValue {

  def applyUnsafe(sinkParameter: AvroSinkValueParameter, parameterValues: Map[String, Any]): AvroSinkValue =
    sinkParameter match {
      case AvroSinkSingleValueParameter(param) =>
        val value = parameterValues(param.name).asInstanceOf[LazyParameter[AnyRef]]
        AvroSinkSingleValue(value)
      case AvroSinkRecordParameter(fields) =>
        val fieldValues = fields.map(_.value.name).map { fieldName =>
          val value = parameterValues(fieldName).asInstanceOf[LazyParameter[AnyRef]]
          (fieldName, value)
        }
        AvroSinkRecordValue(fieldValues)
      case AvroSinkValueEmptyParameter =>
        throw InvalidSinkValueError("Sink value is empty.")
    }
}

private[sink] sealed trait AvroSinkValue

private[sink] case class AvroSinkSingleValue(value: LazyParameter[AnyRef])
  extends AvroSinkValue

private[sink] case class AvroSinkRecordValue(fields: List[(String, LazyParameter[AnyRef])])
  extends AvroSinkValue