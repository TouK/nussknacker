package pl.touk.nussknacker.engine.schemedkafka.schema

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.schemedkafka.AvroDefaultExpressionDeterminer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.parameters.SchemaBasedParameter.ParameterName
import pl.touk.nussknacker.engine.util.parameters.{
  SchemaBasedParameter,
  SchemaBasedRecordParameter,
  SingleSchemaBasedParameter,
  TypingResultValidator
}

import scala.collection.immutable.ListMap

object AvroSchemaBasedParameter {

  import scala.jdk.CollectionConverters._

  /*
    We extract editor form from Avro schema
   */
  def apply(schema: Schema, restrictedParamNames: Set[ParameterName])(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] =
    toSchemaBasedParameter(schema, paramName = None, defaultValue = None, restrictedParamNames = restrictedParamNames)

  private def toSchemaBasedParameter(
      schema: Schema,
      paramName: Option[String],
      defaultValue: Option[Expression],
      restrictedParamNames: Set[ParameterName]
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] = {
    import cats.implicits.{catsStdInstancesForList, toTraverseOps}

    if (schema.getType == Schema.Type.RECORD) {
      val recordFields = schema.getFields.asScala.toList
      if (containsRestrictedNames(recordFields, restrictedParamNames)) {
        Invalid(
          NonEmptyList.one(
            CustomNodeError(
              nodeId.id,
              s"""Record field name is restricted. Restricted names are ${restrictedParamNames.mkString(", ")}""",
              None
            )
          )
        )
      } else {
        val listOfValidatedParams = recordFields.map { recordField =>
          val fieldName = recordField.name()
          // Fields of nested records are flatten, e.g. { a -> { b -> _ } } => { a.b -> _ }
          val concatName = paramName.map(pn => s"$pn.$fieldName").getOrElse(fieldName)
          val sinkValueValidated = getDefaultValue(recordField, paramName).andThen { defaultValue =>
            toSchemaBasedParameter(
              schema = recordField.schema(),
              paramName = Some(concatName),
              defaultValue,
              restrictedParamNames
            )
          }
          sinkValueValidated.map(sinkValueParam => fieldName -> sinkValueParam)
        }
        listOfValidatedParams.sequence.map(l => ListMap(l: _*)).map(SchemaBasedRecordParameter)
      }
    } else {
      Valid(AvroSinkSingleValueParameter(paramName, schema, defaultValue))
    }
  }

  private def getDefaultValue(fieldSchema: Schema.Field, paramName: Option[String])(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, Option[Expression]] =
    new AvroDefaultExpressionDeterminer(handleNotSupported = true)
      .determine(fieldSchema)
      .leftMap(_.map(err => CustomNodeError(err.getMessage, paramName)))

  private def containsRestrictedNames(fields: List[Schema.Field], restrictedParamNames: Set[ParameterName]): Boolean = {
    val fieldNames = fields.map(_.name()).toSet
    fieldNames.nonEmpty & (fieldNames & restrictedParamNames).nonEmpty
  }

}

object AvroSinkSingleValueParameter {

  def apply(paramName: Option[String], schema: Schema, defaultValue: Option[Expression]): SingleSchemaBasedParameter = {
    val typing = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
    val name   = paramName.getOrElse(SinkValueParamName)
    val parameter = (
      if (schema.isNullable) Parameter.optional(name, typing) else Parameter(name, typing)
    ).copy(
      isLazyParameter = true,
      defaultValue = defaultValue
    )
    // TODO: for now we don't use SchemaOutputValidator for avro in editor mode,
    // but we can add it in the future in combination with accepting unknown/any in enums fields to allow passing enums in editor mode
    SingleSchemaBasedParameter(parameter, TypingResultValidator.emptyValidator)
  }

}
