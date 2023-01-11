package pl.touk.nussknacker.engine.json

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import org.everit.json.schema.{ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.swagger.implicits.RichSwaggerTyped
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkRecordParameter, SinkSingleValueParameter, SinkValueParameter}

import scala.collection.immutable.ListMap

object JsonSinkValueParameter {

  import scala.jdk.CollectionConverters._
  import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._

  type FieldName = String

  //Extract editor form from JSON schema
  def apply(schema: Schema, defaultParamName: String)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =
    ParameterRetriever(schema, defaultParamName).toSinkValueParameter(schema, paramName = None, defaultValue = None, isRequired = None)

  private case class ParameterRetriever(wholeSchema: Schema, defaultParamName: String)(implicit nodeId: NodeId) {

    def toSinkValueParameter(schema: Schema, paramName: Option[String], defaultValue: Option[Expression], isRequired: Option[Boolean]): ValidatedNel[ProcessCompilationError, SinkValueParameter] = {
      schema match {
        case objectSchema: ObjectSchema if !objectSchema.hasOnlyAdditionalProperties =>
          objectSchemaToSinkValueParameter(objectSchema, paramName, isRequired = None) //ObjectSchema doesn't use property required
        case _ =>
          Valid(createJsonSinkSingleValueParameter(schema, paramName.getOrElse(defaultParamName), defaultValue, isRequired))
      }
    }

  private def createJsonSinkSingleValueParameter(schema: Schema,
                                                 paramName: String,
                                                 defaultValue: Option[Expression],
                                                 isRequired: Option[Boolean]): SinkSingleValueParameter = {
    val swaggerTyped = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema, Some(wholeSchema))
    val typing = swaggerTyped.typingResult
    //By default properties are not required: http://json-schema.org/understanding-json-schema/reference/object.html#required-properties
    val isOptional = !isRequired.getOrElse(false)
    val parameter = (if (isOptional) Parameter.optional(paramName, typing) else Parameter(paramName, typing))
      .copy(isLazyParameter = true, defaultValue = defaultValue.map(_.expression), editor = swaggerTyped.editorOpt)

      SinkSingleValueParameter(parameter)
    }

    private def objectSchemaToSinkValueParameter(schema: ObjectSchema, paramName: Option[String], isRequired: Option[Boolean]): ValidatedNel[ProcessCompilationError, SinkValueParameter] = {
      import cats.implicits.{catsStdInstancesForList, toTraverseOps}

      val listOfValidatedParams: List[Validated[NonEmptyList[ProcessCompilationError], (String, SinkValueParameter)]] = schema.getPropertySchemas.asScala.map {
        case (fieldName, fieldSchema) =>
          // Fields of nested records are flatten, e.g. { a -> { b -> _ } } => { a.b -> _ }
          val concatName = paramName.fold(fieldName)(pn => s"$pn.$fieldName")
          val isRequired = Option(schema.getRequiredProperties.contains(fieldName))
          val sinkValueValidated = getDefaultValue(fieldSchema, paramName).andThen { defaultValue =>
            toSinkValueParameter(schema = fieldSchema, paramName = Option(concatName), defaultValue = defaultValue, isRequired = isRequired)
          }
          sinkValueValidated.map(sinkValueParam => fieldName -> sinkValueParam)
      }.toList
      listOfValidatedParams.sequence.map(l => ListMap(l: _*)).map(SinkRecordParameter)
    }

    private def getDefaultValue(fieldSchema: Schema, paramName: Option[String]): ValidatedNel[ProcessCompilationError, Option[Expression]] =
      JsonDefaultExpressionDeterminer
        .determineWithHandlingNotSupportedTypes(fieldSchema, paramName)
  }


}
