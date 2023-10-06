package pl.touk.nussknacker.engine.util.sinkvalue

import pl.touk.nussknacker.engine.api.LazyParameter
import pl.touk.nussknacker.engine.util.definition.LazyParameterUtils

import scala.collection.immutable.ListMap

/*
  Intermediate object which helps with mapping Avro/JsonSchema sink editor structure to Avro/JsonSchema message (see SchemaBasedParameter)
 */
object SinkValueData {

  sealed trait SinkValue {
    def toLazyParameter: LazyParameter[AnyRef] = toLazyParameter(this)

    private def toLazyParameter(sv: SinkValue): LazyParameter[AnyRef] = sv match {
      case SinkSingleValue(value) =>
        value
      case SinkRecordValue(fields) =>
        LazyParameterUtils.typedMap(ListMap(fields.toList.map { case (key, value) =>
          key -> toLazyParameter(value)
        }: _*))
    }

  }

  case class SinkSingleValue(value: LazyParameter[AnyRef]) extends SinkValue

  case class SinkRecordValue(fields: ListMap[String, SinkValue]) extends SinkValue

}
