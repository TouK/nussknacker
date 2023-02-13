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
    def toParameters: List[Parameter] = this match {
      case SinkSingleValueParameter(value, _) => value :: Nil
      case SinkRecordParameter(fields) => fields.values.toList.flatMap(_.toParameters)
    }
    def validateParams(resultType: List[(String, BaseDefinedParameter)], prefix: List[String])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit]
  }

  case class SinkSingleValueParameter(value: Parameter, validator: SchemaAwareSinkValueValidator) extends SinkValueParameter {
    //todo: maybe there should be another class/object to validate existing SinkValueParameter tree (maybe it should not be a responsibility of SinkValueParameter)
    //todo: prefix/path probably should be a field in SingleSinkValueParameter/SinkRecordParameter case class
    override def validateParams(resultTypes: List[(String, BaseDefinedParameter)], prefix: List[String])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = resultTypes match {
      case (fieldName, resultType) :: Nil =>
        val converter = new OutputValidatorErrorsConverter(fieldName)
        validator
          .validateTypingResultAgainstSchema(resultType.returnType)
          .leftMap(converter.convertValidationErrors)
          .leftMap(NonEmptyList.one)
      case _ => Validated.invalidNel(CannotCreateObjectError("Unexpected parameter list", nodeId.id))
    }
  }

  case class SinkRecordParameter(fields: ListMap[FieldName, SinkValueParameter]) extends SinkValueParameter {
    // todo: find better way to describe what is fieldsSinkValueParameter and BaseDefinedParameter
    override def validateParams(actualResultTypes: List[(String, BaseDefinedParameter)], path: List[String])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
      fields.map { case (fieldName, sinkValueParameter) =>
        val fieldPath = path :+ fieldName
        val fieldsMatchingPath = actualResultTypes.filter(k => k._1.startsWith(fieldPath.mkString(".")))
        sinkValueParameter.validateParams(fieldsMatchingPath, fieldPath)
      }.toList.sequence.map(_ => ())
    }
  }

  trait SchemaAwareSinkValueValidator {
    def validateTypingResultAgainstSchema(typingResult: TypingResult): ValidatedNel[OutputValidatorError, Unit]
  }

  object SchemaAwareSinkValueValidator {
    val emptyValidator: SchemaAwareSinkValueValidator = (_: TypingResult) => Validated.Valid((): Unit)
  }

}
