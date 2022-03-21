package pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import org.everit.json.schema.{ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.common.sinks.JsonRequestResponseSinkFactory.SinkValueParamName
import pl.touk.nussknacker.engine.lite.components.requestresponse.jsonschema.jsonschemautils.{JsonDefaultExpressionDeterminer, JsonSchemaTypeDefinitionExtractor}
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkRecordParameter, SinkSingleValueParameter, SinkValueParameter}

import scala.collection.immutable.ListMap

object SinkValueParameter {

  import scala.collection.JavaConverters._

  type FieldName = String

  //Extract editor form from JSON schema
  def apply(schema: Schema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =
    toSinkValueParameter(schema, paramName = None, defaultValue = None, isRequired = None)

  private def toSinkValueParameter(schema: Schema, paramName: Option[String], defaultValue: Option[Expression], isRequired: Option[Boolean])
                                  (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = {
    schema match {
      case objectSchema: ObjectSchema =>
        objectSchemaToSinkValueParameter(objectSchema, paramName, isRequired = None) //ObjectSchema doesn't use property required
      case _ =>
        Valid(JsonSinkSingleValueParameter(schema, paramName, defaultValue, isRequired))
    }
  }

  private def objectSchemaToSinkValueParameter(schema: ObjectSchema, paramName: Option[String], isRequired: Option[Boolean])
                                              (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = {

    val listOfValidatedParams: List[(FieldName, Validated[NonEmptyList[ProcessCompilationError], SinkValueParameter])] = schema.getPropertySchemas.asScala.map {
      case (fieldName, fieldSchema) =>
        // Fields of nested records are flatten, e.g. { a -> { b -> _ } } => { a.b -> _ }
        val concatName = paramName.fold(fieldName)(pn => s"$pn.$fieldName")
        val isRequired = Option(schema.getRequiredProperties.contains(fieldName))
        val sinkValueValidated = getDefaultValue(fieldSchema, paramName).andThen { defaultValue =>
          toSinkValueParameter(schema = fieldSchema, paramName = Option(concatName), defaultValue, isRequired)
        }
        fieldName -> sinkValueValidated
    }.toList

    sequence(listOfValidatedParams).map(SinkRecordParameter)
  }

  private def getDefaultValue(fieldSchema: Schema, paramName: Option[String])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Option[Expression]] =
    JsonDefaultExpressionDeterminer
      .determineWithHandlingNotSupportedTypes(fieldSchema)
      .leftMap(_.map(err => CustomNodeError(err.getMessage, paramName)))

  private def sequence(l: List[(FieldName, ValidatedNel[ProcessCompilationError, SinkValueParameter])]): ValidatedNel[ProcessCompilationError, ListMap[FieldName, SinkValueParameter]] = {
    import cats.implicits.{catsStdInstancesForList, toTraverseOps}

    l.map { case (fieldName, validated) =>
      validated.map(sinkValueParam => fieldName -> sinkValueParam)
    }.sequence.map(l => ListMap(l: _*))
  }
}

object JsonSinkSingleValueParameter {
  def apply(schema: Schema, paramName: Option[String], defaultValue: Option[Expression], isRequired: Option[Boolean]): SinkSingleValueParameter = {
    val typing = JsonSchemaTypeDefinitionExtractor.typeDefinition(schema)
    val name = paramName.getOrElse(SinkValueParamName)

    //By default properties are not required: http://json-schema.org/understanding-json-schema/reference/object.html#required-properties
    val isOptional = !isRequired.getOrElse(false)
    val parameter = (
      if (isOptional) Parameter.optional(name, typing) else Parameter(name, typing)
      ).copy(
      isLazyParameter = true,
      defaultValue = defaultValue.map(_.expression)
    )

    SinkSingleValueParameter(parameter)
  }
}