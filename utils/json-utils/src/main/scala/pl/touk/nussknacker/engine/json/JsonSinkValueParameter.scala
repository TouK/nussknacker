package pl.touk.nussknacker.engine.json

import cats.data.Validated.Valid
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import org.everit.json.schema.{ObjectSchema, Schema}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.json.encode.JsonSchemaOutputValidator
import pl.touk.nussknacker.engine.json.swagger.implicits.RichSwaggerTyped
import pl.touk.nussknacker.engine.util.output.OutputValidatorError
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkRecordParameter, SinkSingleValueParameter, SinkValueParameter, TypingResultValidator}

import scala.collection.immutable.ListMap

object JsonSinkValueParameter {

  import pl.touk.nussknacker.engine.util.json.JsonSchemaImplicits._

  import scala.jdk.CollectionConverters._

  type FieldName = String

  private val delimiter: Char = '.'

  //Extract editor form from JSON schema
  def apply(schema: Schema, defaultParamName: FieldName, validationMode: ValidationMode)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =
    ParameterRetriever(schema, defaultParamName, validationMode).toSinkValueParameter(schema, paramName = None, defaultValue = None, isRequired = None)

  //Used to un-flat map with concat name eg. { a.b -> _ } => { a -> { b -> _ } } . Reverse objectSchemaToSinkValueParameter
  def unflattenParameters(flatMap: Map[String, AnyRef]): Map[String, AnyRef] = {
    flatMap.foldLeft(Map.empty[String, AnyRef]) {
      case (result, (key, value)) =>
        if (key.contains(delimiter)) {
          val (parentKey, childKey) = key.span(_ != delimiter)
          val parentValue = result.getOrElse(parentKey, Map.empty[String, AnyRef]).asInstanceOf[Map[String, AnyRef]]
          val childMap = unflattenParameters(Map(childKey.drop(1) -> value))
          result + (parentKey -> (parentValue ++ childMap))
        } else {
          result + (key -> value)
        }
    }
  }

  private case class ParameterRetriever(rootSchema: Schema, defaultParamName: FieldName, validationMode: ValidationMode)(implicit nodeId: NodeId) {

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
      val swaggerTyped = SwaggerBasedJsonSchemaTypeDefinitionExtractor.swaggerType(schema, Some(rootSchema))
      val typing = swaggerTyped.typingResult
      //By default properties are not required: http://json-schema.org/understanding-json-schema/reference/object.html#required-properties
      val isOptional = !isRequired.getOrElse(false)
      val parameter = (if (isOptional) Parameter.optional(paramName, typing) else Parameter(paramName, typing))
        .copy(isLazyParameter = true, defaultValue = defaultValue, editor = swaggerTyped.editorOpt)

      SinkSingleValueParameter(parameter, new JsonSchemaOutputValidator(validationMode).validate(_, schema, Some(rootSchema)))
    }

    private def objectSchemaToSinkValueParameter(schema: ObjectSchema, paramName: Option[String], isRequired: Option[Boolean]): ValidatedNel[ProcessCompilationError, SinkValueParameter] = {
      import cats.implicits.{catsStdInstancesForList, toTraverseOps}

      val listOfValidatedParams: List[Validated[NonEmptyList[ProcessCompilationError], (String, SinkValueParameter)]] = schema.getPropertySchemas.asScala.map {
        case (fieldName, fieldSchema) =>
          // Fields of nested records are flatten, e.g. { a -> { b -> _ } } => { a.b -> _ }
          val concatName = paramName.fold(fieldName)(pn => s"$pn$delimiter$fieldName")
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
