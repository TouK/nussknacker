package pl.touk.nussknacker.engine.util.sinkvalue

import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.api.{LazyParameter, NodeId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.util.definition.LazyParameterUtils
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._

import scala.collection.immutable.ListMap

/*
  Intermediate object which helps with mapping Avro/JsonSchema sink editor structure to Avro/JsonSchema message (see SinkValueParameter)
 */
object SinkValueData {

  sealed trait SinkValue {
    def toLazyParameter: LazyParameter[AnyRef] = toLazyParameter(this)
    private def toLazyParameter(sv: SinkValue): LazyParameter[AnyRef] = sv match {
      case SinkSingleValue(value) =>
        value
      case SinkRecordValue(fields) =>
        LazyParameterUtils.typedMap(ListMap(fields.toList.map {
          case (key, value) => key -> toLazyParameter(value)
        }: _*))
    }
  }

  case class SinkSingleValue(value: LazyParameter[AnyRef]) extends SinkValue

  case class SinkRecordValue(fields: ListMap[String, SinkValue]) extends SinkValue

  type FieldName = String

  trait ParameterValidator {
    def validate(fieldName: FieldName, resultType: TypingResult)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit]
  }

  sealed trait SinkValueParameter {
    def toParameters: List[Parameter] = this match {
      case SinkSingleValueParameter(value, _) => value :: Nil
      case SinkRecordParameter(fields) => fields.values.toList.flatMap(_.toParameters)
    }
    def validateParams(resultType: List[(String, TypingResult)], prefix: List[String])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit]
  }

  case class SinkSingleValueParameter(value: Parameter, validator: ParameterValidator) extends SinkValueParameter {
    override def validateParams(resultTypes: List[(String, TypingResult)], prefix: List[String])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = resultTypes match {
      case (a, resultType) :: Nil => validator.validate(a, resultType)
      case _ => ???
    }
  }

  case class SinkRecordParameter(fields: ListMap[FieldName, SinkValueParameter]) extends SinkValueParameter {
    override def validateParams(resultTypes: List[(String, TypingResult)], prefix: List[String])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
      fields.map { case (fieldName, param) =>
        val newPrefix = prefix :+ fieldName
        val prefixed = resultTypes.filter(k => k._1.startsWith(newPrefix.mkString(".")))
        param.validateParams(prefixed, newPrefix)
      }.toList.sequence.map(_ => ())
    }
  }
}

