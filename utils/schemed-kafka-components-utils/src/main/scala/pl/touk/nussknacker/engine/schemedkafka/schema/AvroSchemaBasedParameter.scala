package pl.touk.nussknacker.engine.schemedkafka.schema

import cats.data.{Validated, ValidatedNel}
import cats.data.Validated.Valid
import cats.implicits._
import org.apache.avro.Schema
import org.apache.avro.Schema.Type
import org.apache.avro.generic.GenericData
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.definition._
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.schemedkafka.AvroDefaultExpressionDeterminer
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer._
import pl.touk.nussknacker.engine.schemedkafka.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.output.{OutputValidatorError, OutputValidatorExpected, OutputValidatorValueError}
import pl.touk.nussknacker.engine.util.parameters.{
  SchemaBasedParameter,
  SchemaBasedRecordParameter,
  SingleSchemaBasedParameter,
  TypingResultValidator
}

import scala.collection.immutable.ListMap
import scala.jdk.CollectionConverters._

object AvroSchemaBasedParameter {

  import scala.jdk.CollectionConverters._

  /*
    We extract editor form from Avro schema
   */
  def apply(
      schema: Schema,
      restrictedParamNames: Set[ParameterName],
      avroSchemaTypeDefinitionExtractor: AvroSchemaTypeDefinitionExtractor
  )(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] =
    toSchemaBasedParameter(
      schema,
      paramName = None,
      defaultValue = None,
      restrictedParamNames = restrictedParamNames,
      avroSchemaTypeDefinitionExtractor
    )

  private def toSchemaBasedParameter(
      schema: Schema,
      paramName: Option[ParameterName],
      defaultValue: Option[Expression],
      restrictedParamNames: Set[ParameterName],
      avroSchemaTypeDefinitionExtractor: AvroSchemaTypeDefinitionExtractor
  )(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SchemaBasedParameter] = {
    schema.getType match {
      case Schema.Type.RECORD =>
        val recordFields = schema.getFields.asScala.toList
        Validated
          .condNel(
            !containsRestrictedNames(recordFields, restrictedParamNames),
            (),
            CustomNodeError(
              s"""Record field name is restricted. Restricted names are ${restrictedParamNames
                  .map(_.value)
                  .mkString(", ")}""",
              None
            ),
          )
          .andThen { (_: Unit) =>
            val listOfValidatedParams = recordFields.map { recordField =>
              val fieldName = recordField.name()
              // Fields of nested records are flatten, e.g. { a -> { b -> _ } } => { a.b -> _ }
              val concatName = ParameterName(paramName.map(pn => s"${pn.value}.$fieldName").getOrElse(fieldName))
              val sinkValueValidated = getDefaultValue(recordField, paramName).andThen { defaultValue =>
                toSchemaBasedParameter(
                  schema = recordField.schema(),
                  paramName = Some(concatName),
                  defaultValue,
                  restrictedParamNames,
                  avroSchemaTypeDefinitionExtractor
                )
              }
              sinkValueValidated.map(sinkValueParam => fieldName -> sinkValueParam)
            }
            listOfValidatedParams.sequence.map(l => ListMap(l: _*)).map(SchemaBasedRecordParameter)
          }
      case other =>
        Valid(AvroSinkSingleValueParameter(paramName, schema, defaultValue, avroSchemaTypeDefinitionExtractor))
    }
  }

  private def getDefaultValue(fieldSchema: Schema.Field, paramName: Option[ParameterName])(
      implicit nodeId: NodeId
  ): ValidatedNel[ProcessCompilationError, Option[Expression]] =
    new AvroDefaultExpressionDeterminer(handleNotSupported = true)
      .determine(fieldSchema)
      .leftMap(_.map(err => CustomNodeError(err.getMessage, paramName)))

  private def containsRestrictedNames(fields: List[Schema.Field], restrictedParamNames: Set[ParameterName]): Boolean = {
    val fieldNames = fields.map(_.name()).toSet
    fieldNames.nonEmpty && (fieldNames intersect restrictedParamNames.map(_.value)).nonEmpty
  }

}

object AvroSinkSingleValueParameter {

  def apply(
      paramName: Option[ParameterName],
      schema: Schema,
      defaultValue: Option[Expression],
      avroSchemaTypeDefinitionExtractor: AvroSchemaTypeDefinitionExtractor,
  ): SingleSchemaBasedParameter = {
    val enrichedSchema: Schema = schema.getType match {
      case Schema.Type.ENUM =>
        Schema.createUnion(schema, Schema.create(Type.STRING))
      case Schema.Type.UNION if isUnionWithEnumSchema(schema) =>
        val stringSchema = Schema.create(Type.STRING)
        val unionTypes   = schema.getTypes.asScala.toList
        if (unionTypes.contains(stringSchema)) {
          schema
        } else {
          Schema.createUnion((unionTypes :+ stringSchema).asJava)
        }
      case _ =>
        schema
    }

    val typing = avroSchemaTypeDefinitionExtractor.typeDefinition(enrichedSchema)
    val name   = paramName.getOrElse(sinkValueParamName)
    val (resultValidator, customEditors) = enrichedSchema match {
      // enum schemas will always be here inside the union
      case UnionWithEnumSchema(enumSchema: EnumSchema) =>
        (
          new AvroEnumTypingResultValidator(name.value, enumSchema),
          avroEnumEditors(enumSchema)
        )
      case _ =>
        // TODO: for now, we don't use SchemaOutputValidator for Avro in editor mode, but we can add it in the future
        (TypingResultValidator.emptyValidator, List.empty[ParameterEditor])
    }

    val parameter = (
      if (enrichedSchema.isNullable) Parameter.optional(name, typing) else Parameter(name, typing)
    ).copy(
      isLazyParameter = true,
      defaultValue = defaultValue,
      editors = customEditors
    )

    SingleSchemaBasedParameter(parameter, resultValidator)
  }

  private def isUnionWithEnumSchema(schema: Schema) = {
    schema.getType == Schema.Type.UNION && schema.getTypes.asScala.exists(_.getType == Schema.Type.ENUM)
  }

  private def avroEnumEditors(schema: EnumSchema): List[ParameterEditor] = {
    List(FixedValuesParameterEditor(schema.possibleValues), SpelParameterEditor)
  }

  private final class AvroEnumTypingResultValidator(field: String, enumSchema: EnumSchema)
      extends TypingResultValidator {

    override def validate(typingResult: typing.TypingResult): ValidatedNel[OutputValidatorError, Unit] = {
      typingResult.valueOpt match {
        case Some(_: GenericData.EnumSymbol) =>
          Valid(())
        case Some(value: String) =>
          val schemaSymbols = enumSchema.schema.getEnumSymbols.asScala.toSet
          Validated.condNel(
            schemaSymbols.nonEmpty && schemaSymbols.contains(value),
            (),
            avroEnumExpectedError(typingResult)
          )
        case Some(null) =>
          Validated.condNel(enumSchema.nullable, (), avroEnumExpectedError(typingResult))
        case Some(_) | None =>
          Valid(())
      }
    }

    private def avroEnumExpectedError(actual: TypingResult) = {
      OutputValidatorValueError(
        Some(field),
        actual,
        new AvroEnumTypingResultValidator.AvroEnumExpected(enumSchema)
      )
    }

  }

  private object AvroEnumTypingResultValidator {

    final class AvroEnumExpected(schema: EnumSchema) extends OutputValidatorExpected {
      override def expected: String =
        s"one of: [${schema.possibleValues.map(_.expression).mkString(", ")}]"
    }

  }

  private final class EnumSchema private (val schema: Schema, val nullable: Boolean) {

    def possibleValues: List[FixedExpressionValue] = {
      val enumSymbols = schema.getEnumSymbols.asScala.toList.map(symbol => FixedExpressionValue(s"'$symbol'", symbol))
      if (nullable) {
        FixedExpressionValue("null", "") :: enumSymbols
      } else {
        enumSymbols
      }
    }

  }

  private object EnumSchema {

    def from(schema: Schema, nullable: Boolean): Option[EnumSchema] = {
      Option.when(schema.getType == Schema.Type.ENUM)(new EnumSchema(schema, nullable))
    }

  }

  private object UnionWithEnumSchema {

    def unapply(schema: Schema): Option[EnumSchema] = {
      for {
        unionSchema <- Option.when(schema.getType == Schema.Type.UNION)(schema)
        enumSchema  <- unionSchema.getTypes.asScala.flatMap(EnumSchema.from(_, unionSchema.isNullable)).headOption
      } yield enumSchema
    }

  }

}
