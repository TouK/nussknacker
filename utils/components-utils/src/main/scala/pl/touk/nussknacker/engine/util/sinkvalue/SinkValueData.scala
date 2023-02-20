package pl.touk.nussknacker.engine.util.sinkvalue

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.transformation.BaseDefinedParameter
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{LazyParameter, NodeId}
import pl.touk.nussknacker.engine.util.definition.LazyParameterUtils
import pl.touk.nussknacker.engine.util.output.{OutputValidatorError, OutputValidatorErrorsConverter}
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

  sealed trait SinkValueParameter {
    def toParameters: List[Parameter] = flatten.map(_.value)

    def flatten: List[SinkSingleValueParameter] = this match {
      case single: SinkSingleValueParameter => single :: Nil
      case SinkRecordParameter(fields) => fields.values.toList.flatMap(_.flatten)
    }
    def validateParams(resultType: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit]
  }

  case class SinkSingleValueParameter(value: Parameter, validator: SchemaOutputValidator) extends SinkValueParameter {
    override def validateParams(resultTypes: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
      resultTypes.filter { case (name, _) =>
        name == value.name
      }.map {
        case (fieldName, resultType) =>
          val converter = new OutputValidatorErrorsConverter(fieldName)
          validator
            .validateTypingResultAgainstSchema(resultType.returnType)
            .leftMap(converter.convertValidationErrors)
            .leftMap(NonEmptyList.one)
      }.sequence.map(_ => ())
    }
  }

  case class SinkRecordParameter(fields: ListMap[FieldName, SinkValueParameter]) extends SinkValueParameter {
    override def validateParams(actualResultTypes: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
      flatten.map(_.validateParams(actualResultTypes)).sequence.map(_ => ())
    }
  }

  trait SchemaOutputValidator {
    def validateTypingResultAgainstSchema(typingResult: TypingResult): ValidatedNel[OutputValidatorError, Unit]
  }

  object SchemaOutputValidator {
    val emptyValidator: SchemaOutputValidator = (_: TypingResult) => Validated.Valid((): Unit)
  }

}
