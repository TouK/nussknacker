package pl.touk.nussknacker.engine.avro.sink

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CustomNodeError, NodeId}
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.avro.KafkaAvroBaseComponentTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.avro.sink.AvroSinkValueParameter.FieldName
import pl.touk.nussknacker.engine.avro.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.definition.parameter.editor.ParameterTypeEditorDeterminer


object AvroSinkValueParameter {

  import scala.collection.JavaConverters._

  type FieldName = String

  val restrictedParamNames: Set[FieldName] =
    Set(SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, TopicParamName)

  /*
    We extract editor form from Avro schema
   */
  def apply(schema: Schema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, AvroSinkValueParameter] =
    toSinkValueParameter(schema, paramName = None)

  private def toSinkValueParameter(schema: Schema, paramName: Option[String])
                                  (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, AvroSinkValueParameter] =
    if (schema.getType == Schema.Type.RECORD) {
      val recordFields = schema.getFields.asScala.toList
      if (containsRestrictedNames(recordFields)) {
        /* TODO: Since GenericNodeTransformation#implementation passes all parameters in a single Map we need to restrict value parameter names,
         so they do not collide with other parameters like Topic or Key. */
        Invalid(NonEmptyList.one(
          CustomNodeError(nodeId.id, s"""Record field name is restricted. Restricted names are ${restrictedParamNames.mkString(", ")}""", None)))
      } else {
        val listOfValidatedParams = recordFields.map { recordField =>
          val fieldName = recordField.name()
          val fieldSchema = recordField.schema()
          // Fields of nested records are flatten, e.g. { a -> { b -> _ } } => { a.b -> _ }
          val concatName = paramName.map(pn => s"$pn.$fieldName").getOrElse(fieldName)
          fieldName -> toSinkValueParameter(paramName = Some(concatName), schema = fieldSchema)
        }
        sequence(listOfValidatedParams).map(AvroSinkRecordParameter)
      }
    } else {
      Valid(AvroSinkSingleValueParameter(paramName, schema))
    }

  private def containsRestrictedNames(fields: List[Schema.Field]): Boolean = {
    val fieldNames = fields.map(_.name()).toSet
    fieldNames.nonEmpty & (fieldNames & restrictedParamNames).nonEmpty
  }

  private def sequence(l: List[(FieldName, ValidatedNel[ProcessCompilationError, AvroSinkValueParameter])])
  : ValidatedNel[ProcessCompilationError, List[(FieldName, AvroSinkValueParameter)]] = {
    import cats.implicits.{catsStdInstancesForList, toTraverseOps}
    l.map { case (fieldName, validated) =>
      validated.map(sinkValueParam => fieldName -> sinkValueParam)
    }.sequence
  }
}

/**
  * This trait maps TypingResult information to structure of Avro sink editor (and then to Avro message), see AvroSinkValue
  */
sealed trait AvroSinkValueParameter {

  def toParameters: List[Parameter] = this match {
    case AvroSinkSingleValueParameter(value) => value :: Nil
    case AvroSinkRecordParameter(fields) => fields.flatMap(_._2.toParameters)
  }
}

object AvroSinkSingleValueParameter {

  def apply(paramName: Option[String], schema: Schema): AvroSinkSingleValueParameter = {
    val typing = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
    val name = paramName.getOrElse(SinkValueParamName)
    val parameter = (
      if (schema.isNullable) Parameter.optional(name, typing) else Parameter(name, typing)
    ).copy(
      isLazyParameter = true,
      editor = new ParameterTypeEditorDeterminer(typing).determine()
    )
    AvroSinkSingleValueParameter(parameter)
  }
}

case class AvroSinkSingleValueParameter(value: Parameter)
  extends AvroSinkValueParameter

case class AvroSinkRecordParameter(fields: List[(FieldName, AvroSinkValueParameter)])
  extends AvroSinkValueParameter
