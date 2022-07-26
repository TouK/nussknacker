package pl.touk.nussknacker.engine.avro.sink

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import org.apache.avro.Schema
import org.everit.json.schema
import org.everit.json.schema.ObjectSchema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.AvroDefaultExpressionDeterminer
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.json.JsonSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SinkRecordParameter, SinkSingleValueParameter, SinkValueParameter}

import scala.collection.JavaConverters._
import scala.collection.immutable.ListMap

class AvroSinkValueParameterExtractor extends AbstractSinkValueParameter[Schema, Schema.Field] {
  override def representsObject(schema: Schema): Boolean = schema.getType == Schema.Type.RECORD
  override def getFields(schema: Schema): List[Schema.Field] = schema.getFields.asScala.toList
  override def getFieldName(field: Schema.Field): FieldName = field.name()
  override def getSchema(field: Schema.Field): Schema = field.schema()
  override def getTyping(schema: Schema): TypingResult = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
  override def isNullable(schema: Schema): Boolean = schema.isNullable
  override def getDefaultValue(fieldSchema: Schema.Field, paramName: Option[FieldName])
                              (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Option[Expression]] =
    new AvroDefaultExpressionDeterminer(handleNotSupported = true).determine(fieldSchema)
    .leftMap(_.map(err => CustomNodeError(err.getMessage, paramName)))
}

class JsonSinkValueParameterExtractor extends AbstractSinkValueParameter[org.everit.json.schema.Schema, (String, org.everit.json.schema.Schema)] {
  override def representsObject(schema: org.everit.json.schema.Schema): Boolean = schema.isInstanceOf[ObjectSchema]
  override def getFields(schema: org.everit.json.schema.Schema): List[(FieldName, org.everit.json.schema.Schema)] = schema.asInstanceOf[ObjectSchema].getPropertySchemas.asScala.toList
  override def getFieldName(field: (FieldName, org.everit.json.schema.Schema)): FieldName = field._1
  override def getSchema(field: (FieldName, org.everit.json.schema.Schema)): schema.Schema = field._2
  override def getTyping(schema: org.everit.json.schema.Schema): TypingResult = JsonSchemaTypeDefinitionExtractor.typeDefinition(schema)
  override def isNullable(schema: org.everit.json.schema.Schema): Boolean = schema.isNullable
  override def getDefaultValue(fieldSchema: (FieldName, schema.Schema), paramName: Option[FieldName])
                              (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Option[Expression]] = Valid(None) //todo
}

abstract class AbstractSinkValueParameter[S, F] {

  type FieldName = String

  val restrictedParamNames: Set[FieldName] =
    Set(SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, TopicParamName)

  def extract(schema: S)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =
    toSinkValueParameter(schema, paramName = None, defaultValue = None)


  protected def representsObject(schema: S): Boolean
  protected def getFields(schema: S): List[F]
  protected def getFieldName(field: F): String
  protected def getSchema(field: F): S
  protected def getTyping(schema: S): TypingResult
  protected def isNullable(schema: S): Boolean

  private def toSinkValueParameter(schema: S, paramName: Option[String], defaultValue: Option[Expression])
                                  (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = {
    import cats.implicits.{catsStdInstancesForList, toTraverseOps}

    if (representsObject(schema)) {
      val recordFields = getFields(schema)
      if (containsRestrictedNames(recordFields)) {
        /* TODO: Since GenericNodeTransformation#implementation passes all parameters in a single Map we need to restrict value parameter names,
         so they do not collide with other parameters like Topic or Key. */
        Invalid(NonEmptyList.one(
          CustomNodeError(nodeId.id, s"""Record field name is restricted. Restricted names are ${restrictedParamNames.mkString(", ")}""", None)))
      } else {
        val listOfValidatedParams = recordFields.map { recordField =>
          val fieldName = getFieldName(recordField)
          // Fields of nested records are flatten, e.g. { a -> { b -> _ } } => { a.b -> _ }
          val concatName = paramName.map(pn => s"$pn.$fieldName").getOrElse(fieldName)
          val sinkValueValidated = getDefaultValue(recordField, paramName).andThen { defaultValue =>
            toSinkValueParameter(schema = getSchema(recordField), paramName = Some(concatName), defaultValue)
          }
          sinkValueValidated.map(sinkValueParam => fieldName -> sinkValueParam)
        }
        listOfValidatedParams.sequence.map(l => ListMap(l: _*)).map(SinkRecordParameter)
      }
    } else {
      Valid(create(paramName, schema, defaultValue))
    }
  }

  def getDefaultValue(fieldSchema: F, paramName: Option[String])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Option[Expression]]

  private def containsRestrictedNames(fields: List[F]): Boolean = {
    val fieldNames = fields.map(getFieldName).toSet
    fieldNames.nonEmpty & (fieldNames & restrictedParamNames).nonEmpty
  }

  private def create(paramName: Option[String], schema: S, defaultValue: Option[Expression]): SinkSingleValueParameter = {
    val typing = getTyping(schema)
    val name = paramName.getOrElse(SinkValueParamName)
    val parameter = (
      if (isNullable(schema)) Parameter.optional(name, typing) else Parameter(name, typing)
      ).copy(
      isLazyParameter = true,
      defaultValue = defaultValue.map(_.expression)
    )
    SinkSingleValueParameter(parameter)
  }
}
