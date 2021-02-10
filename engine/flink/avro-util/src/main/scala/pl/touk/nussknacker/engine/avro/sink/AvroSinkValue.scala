package pl.touk.nussknacker.engine.avro.sink

import pl.touk.nussknacker.engine.api.LazyParameter

object AvroSinkValue {
  case class InvalidSinkValue(parameterName: String)
    extends Exception(s"Parameter: $parameterName must be a LazyParameter[AnyRef] instance.")

  def applyUnsafe(sinkParameter: AvroSinkValueParameter, parameterValues: Map[String, Any]): AvroSinkValue =
    sinkParameter match {
      case AvroSinkPrimitiveValueParameter(param) =>
        val value = parameterValues(param.name)
        AvroSinkSingleValue(toLazyParameter(value, param.name))

      case AvroSinkRecordParameter(paramFields) =>
        val fields = paramFields.map { case (fieldName, sinkParam) =>
          (fieldName, applyUnsafe(sinkParam, parameterValues))
        }
        AvroSinkRecordValue(fields)
    }

  private def toLazyParameter(a: Any, paramName: String): LazyParameter[AnyRef] =
    try {
      a.asInstanceOf[LazyParameter[AnyRef]]
    } catch {
      case _: ClassCastException => throw InvalidSinkValue(paramName)
    }
}

private[sink] sealed trait AvroSinkValue

private[sink] case class AvroSinkSingleValue(value: LazyParameter[AnyRef])
  extends AvroSinkValue

private[sink] case class AvroSinkRecordValue(fields: Map[String, AvroSinkValue])
  extends AvroSinkValue