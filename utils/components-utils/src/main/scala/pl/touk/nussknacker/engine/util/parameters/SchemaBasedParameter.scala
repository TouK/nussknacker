package pl.touk.nussknacker.engine.util.parameters

import cats.data.{NonEmptyList, ValidatedNel}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.transformation.BaseDefinedParameter
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.util.output.OutputValidatorErrorsConverter
import pl.touk.nussknacker.engine.util.parameters.SchemaBasedParameter.RecordFieldName
import pl.touk.nussknacker.engine.util.validated.ValidatedSyntax._

import scala.collection.immutable.ListMap

// ParameterName can be created by concatenating names of records/object fields (RecordFieldName) - see AvroSchemaBasedParameter/JsonSchemaBasedParameter
sealed trait SchemaBasedParameter {
  def toParameters: List[Parameter] = flatten.map(_.value)

  def flatten: List[SingleSchemaBasedParameter] = this match {
    case single: SingleSchemaBasedParameter => single :: Nil
    case SchemaBasedRecordParameter(fields) => fields.values.toList.flatMap(_.flatten)
  }

  def validateParams(resultType: Map[ParameterName, BaseDefinedParameter])(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, Unit]

}

object SchemaBasedParameter {
  type RecordFieldName = String
}

case class SingleSchemaBasedParameter(value: Parameter, validator: TypingResultValidator) extends SchemaBasedParameter {

  override def validateParams(
      resultTypes: Map[ParameterName, BaseDefinedParameter]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    val paramName       = value.name
    val paramResultType = resultTypes(paramName)
    val converter       = new OutputValidatorErrorsConverter(paramName)
    validator
      .validate(paramResultType.returnType)
      .leftMap(converter.convertValidationErrors)
      .leftMap(NonEmptyList.one)
      .map(_ => ())
  }

}

case class SchemaBasedRecordParameter(fields: ListMap[RecordFieldName, SchemaBasedParameter])
    extends SchemaBasedParameter {

  override def validateParams(
      actualResultTypes: Map[ParameterName, BaseDefinedParameter]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Unit] = {
    flatten.map(_.validateParams(actualResultTypes)).sequence.map(_ => ())
  }

}
