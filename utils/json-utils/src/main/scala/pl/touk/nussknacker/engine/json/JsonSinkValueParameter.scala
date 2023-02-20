package pl.touk.nussknacker.engine.json

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import org.everit.json.schema.{ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.encode.JsonSchemaOutputValidator
import pl.touk.nussknacker.engine.json.swagger.implicits.RichSwaggerTyped
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkRecordParameter, SinkSingleValueParameter, SinkValueParameter}

import scala.collection.immutable.ListMap

object JsonSinkValueParameter {

  import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._

  import scala.jdk.CollectionConverters._

  type FieldName = String

  //Extract editor form from JSON schema
  def apply(schema: Schema, defaultParamName: FieldName, validationMode: ValidationMode)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =
    ParameterRetriever(schema, List(defaultParamName), validationMode).toSinkValueParameter(schema, Nil, defaultValue = None, isRequired = None)

  private case class ParameterRetriever(rootSchema: Schema, defaultPath: List[String], validationMode: ValidationMode)(implicit nodeId: NodeId) {

    def toSinkValueParameter(schema: Schema, path: List[String], defaultValue: Option[Expression], isRequired: Option[Boolean]): ValidatedNel[ProcessCompilationError, SinkValueParameter] = {
      schema match {
        case objectSchema: ObjectSchema if !objectSchema.hasOnlyAdditionalProperties =>
          objectSchemaToSinkValueParameter(objectSchema, path, isRequired = None) //ObjectSchema doesn't use property required
        case _ =>
          Valid(createJsonSinkSingleValueParameter(schema, Option(path).filter(_.nonEmpty).getOrElse(defaultPath), defaultValue, isRequired))
      }
    }

  private def createJsonSinkSingleValueParameter(schema: Schema,
                                                 path: List[String],
                                                 defaultValue: Option[Expression],
                                                 isRequired: Option[Boolean]): SinkSingleValueParameter = {
    val swaggerTyped = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema, Some(rootSchema))
    val typing = swaggerTyped.typingResult
    //By default properties are not required: http://json-schema.org/understanding-json-schema/reference/object.html#required-properties
    val isOptional = !isRequired.getOrElse(false)
    val name = toFlatParameterName(path)
    val parameter = (if (isOptional) Parameter.optional(name, typing) else Parameter(name, typing))
      .copy(isLazyParameter = true, defaultValue = defaultValue.map(_.expression), editor = swaggerTyped.editorOpt)

      SinkSingleValueParameter(path, parameter, new JsonSchemaOutputValidator(validationMode, schema, rootSchema))
    }

    private def objectSchemaToSinkValueParameter(schema: ObjectSchema, path: List[String], isRequired: Option[Boolean]): ValidatedNel[ProcessCompilationError, SinkValueParameter] = {
      import cats.implicits.{catsStdInstancesForList, toTraverseOps}

      val listOfValidatedParams: List[Validated[NonEmptyList[ProcessCompilationError], (String, SinkValueParameter)]] = schema.getPropertySchemas.asScala.map {
        case (fieldName, fieldSchema) =>
          val newPath = fieldName :: path
          val isRequired = Option(schema.getRequiredProperties.contains(fieldName))
          val sinkValueValidated = getDefaultValue(fieldSchema, newPath).andThen { defaultValue =>
            toSinkValueParameter(schema = fieldSchema, path = newPath, defaultValue = defaultValue, isRequired = isRequired)
          }
          sinkValueValidated.map(sinkValueParam => fieldName -> sinkValueParam)
      }.toList
      listOfValidatedParams.sequence.map(l => ListMap(l: _*)).map(SinkRecordParameter(path, _))
    }

    private def getDefaultValue(fieldSchema: Schema, path: List[String]): ValidatedNel[ProcessCompilationError, Option[Expression]] =
      JsonDefaultExpressionDeterminer
        .determineWithHandlingNotSupportedTypes(fieldSchema, toFlatParameterName(path))

    private def toFlatParameterName(path: List[String]) = path.reverse.mkString(",")

  }

}
