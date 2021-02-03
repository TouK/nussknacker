package pl.touk.nussknacker.engine.avro.sink

import pl.touk.nussknacker.engine.api.LazyParameter

object AvroSinkValue {

  def applyUnsafe(sinkParameter: AvroSinkValueParameter, parameterValues: Map[String, Any]): AvroSinkValue =
    sinkParameter match {
      case AvroSinkPrimitiveValueParameter(param) =>
        val value = parameterValues(param.name).asInstanceOf[LazyParameter[AnyRef]]
        AvroSinkPrimitiveValue(value)

      case AvroSinkRecordParameter(paramFields) =>
        val fields = paramFields.map { case (fieldName, sinkParam) =>
          (fieldName, applyUnsafe(sinkParam, parameterValues))
        }
        AvroSinkRecordValue(fields)
    }
}

private[sink] sealed trait AvroSinkValue

private[sink] case class AvroSinkPrimitiveValue(value: LazyParameter[AnyRef])
  extends AvroSinkValue

private[sink] case class AvroSinkRecordValue(fields: Map[String, AvroSinkValue])
  extends AvroSinkValue