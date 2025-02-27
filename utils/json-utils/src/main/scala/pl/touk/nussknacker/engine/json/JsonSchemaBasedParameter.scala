package pl.touk.nussknacker.engine.json

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.Valid
import org.everit.json.schema.{ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.encode.JsonSchemaOutputValidator
import pl.touk.nussknacker.engine.json.swagger.implicits.RichSwaggerTyped
import pl.touk.nussknacker.engine.util.parameters.{
  SchemaBasedParameter,
  SchemaBasedRecordParameter,
  SingleSchemaBasedParameter,
  TestingParametersSupport
}

import scala.collection.immutable.ListMap

object JsonSchemaBasedParameter {

  import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._

  import scala.jdk.CollectionConverters._

  type FieldName = ParameterName

  // Extract editor form from JSON schema
  def apply(schema: Schema, defaultParamName: FieldName, validationMode: ValidationMode)(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] =
    ParameterRetriever(schema, defaultParamName, validationMode).toSchemaBasedParameter(
      schema,
      paramName = None,
      defaultValue = None,
      isRequired = None
    )

  private case class ParameterRetriever(
      rootSchema: Schema,
      defaultParamName: FieldName,
      validationMode: ValidationMode
  )(implicit nodeId: NodeId) {

    def toSchemaBasedParameter(
        schema: Schema,
        paramName: Option[ParameterName],
        defaultValue: Option[Expression],
        isRequired: Option[Boolean]
    ): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] = {
      schema match {
        case objectSchema: ObjectSchema if !objectSchema.hasOnlyAdditionalProperties =>
          objectSchemaToSchemaBasedParameter(
            objectSchema,
            paramName,
            isRequired = None
          ) // ObjectSchema doesn't use property required
        case _ =>
          Valid(
            createJsonSinkSingleValueParameter(schema, paramName.getOrElse(defaultParamName), defaultValue, isRequired)
          )
      }
    }

    private def createJsonSinkSingleValueParameter(
        schema: Schema,
        paramName: ParameterName,
        defaultValue: Option[Expression],
        isRequired: Option[Boolean]
    ): SingleSchemaBasedParameter = {
      val swaggerTyped = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema, Some(rootSchema))
      val typing       = swaggerTyped.typingResult
      // By default properties are not required: http://json-schema.org/understanding-json-schema/reference/object.html#required-properties
      val isOptional = !isRequired.getOrElse(false)
      val parameter = (if (isOptional) Parameter.optional(paramName, typing) else Parameter(paramName, typing))
        .copy(isLazyParameter = true, defaultValue = defaultValue, editor = swaggerTyped.editorOpt)

      SingleSchemaBasedParameter(
        parameter,
        new JsonSchemaOutputValidator(validationMode).validate(_, schema, Some(rootSchema))
      )
    }

    private def objectSchemaToSchemaBasedParameter(
        schema: ObjectSchema,
        paramName: Option[ParameterName],
        isRequired: Option[Boolean]
    ): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] = {
      import cats.implicits.{catsStdInstancesForList, toTraverseOps}

      val listOfValidatedParams
          : List[Validated[NonEmptyList[ProcessCompilationError], (String, SchemaBasedParameter)]] =
        schema.getPropertySchemas.asScala.map { case (fieldName, fieldSchema) =>
          // Fields of nested records are flatten, e.g. { a -> { b -> _ } } => { a.b -> _ }
          val concatName = ParameterName(
            paramName.fold(fieldName)(pn => TestingParametersSupport.joinWithDelimiter(pn.value, fieldName))
          )
          val isRequired = Option(schema.getRequiredProperties.contains(fieldName))
          val schemaBasedValidated = getDefaultValue(fieldSchema, paramName).andThen { defaultValue =>
            toSchemaBasedParameter(
              schema = fieldSchema,
              paramName = Option(concatName),
              defaultValue = defaultValue,
              isRequired = isRequired
            )
          }
          schemaBasedValidated.map(schemaBasedParam => fieldName -> schemaBasedParam)
        }.toList
      listOfValidatedParams.sequence.map(l => ListMap(l: _*)).map(SchemaBasedRecordParameter)
    }

    private def getDefaultValue(
        fieldSchema: Schema,
        paramName: Option[ParameterName]
    ): ValidatedNel[ProcessCompilationError, Option[Expression]] =
      JsonDefaultExpressionDeterminer
        .determineWithHandlingNotSupportedTypes(fieldSchema, paramName)

  }

}
