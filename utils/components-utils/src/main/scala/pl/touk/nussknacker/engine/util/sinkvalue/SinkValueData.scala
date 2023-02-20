package pl.touk.nussknacker.engine.util.sinkvalue

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.api.{LazyParameter, NodeId}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CannotCreateObjectError
import pl.touk.nussknacker.engine.api.context.transformation.BaseDefinedParameter
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.util.definition.LazyParameterUtils
import pl.touk.nussknacker.engine.util.output.{OutputValidatorError, OutputValidatorErrorsConverter}
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._

import scala.collection.immutable.ListMap

/*
  Intermediate object which helps with mapping Avro/JsonSchema sink editor structure to Avro/JsonSchema message (see SinkValueParameter)
 */
object SinkValueData {

  sealed trait SinkValue {
    def toLazyParameter: LazyParameter[AnyRef]

  }

  case class SinkSingleValue(value: LazyParameter[AnyRef]) extends SinkValue {
    override def toLazyParameter: LazyParameter[AnyRef] = value
  }

  case class SinkRecordValue(fields: ListMap[String, SinkValue]) extends SinkValue {
    override def toLazyParameter: LazyParameter[AnyRef] =
      LazyParameterUtils.typedMap(ListMap(fields.toList.map {
        case (key, value) => key -> value.toLazyParameter
      }: _*))
  }

  type FieldName = String

  sealed trait SinkValueParameter {
    def path: List[String]

    def pathString: String = path.reverse.mkString(".")

    def toParameters: List[Parameter] = this match {
      case SinkSingleValueParameter(_, value, _) => value :: Nil
      case SinkRecordParameter(_, fields) => fields.values.toList.flatMap(_.toParameters)
    }
    def validateParams(resultType: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit]
  }

  case class SinkSingleValueParameter(path: List[String], value: Parameter, validator: SchemaOutputValidator) extends SinkValueParameter {
    override def validateParams(resultTypes: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = resultTypes match {
      case (fieldName, resultType) :: Nil =>
        val converter = new OutputValidatorErrorsConverter(fieldName)
        validator
          .validateTypingResultAgainstSchema(resultType.returnType)
          .leftMap(converter.convertValidationErrors)
          .leftMap(NonEmptyList.one)
      case _ => Validated.invalidNel(CannotCreateObjectError("Unexpected parameter list", nodeId.id))
    }
  }

  case class SinkRecordParameter(path: List[String], fields: ListMap[FieldName, SinkValueParameter]) extends SinkValueParameter {
    override def validateParams(actualResultTypes: List[(String, BaseDefinedParameter)])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
      fields.map { case (_, sinkValueParameter) =>
        val fieldsMatchingPath = actualResultTypes.filter { case (name, _) => name.startsWith(sinkValueParameter.pathString) }
        sinkValueParameter.validateParams(fieldsMatchingPath)
      }.toList.sequence.map(_ => ())
    }
  }

  trait SchemaOutputValidator {
    def validateTypingResultAgainstSchema(typingResult: TypingResult): ValidatedNel[OutputValidatorError, Unit]
  }

  object SchemaOutputValidator {
    val emptyValidator: SchemaOutputValidator = (_: TypingResult) => Validated.Valid((): Unit)
  }

}
