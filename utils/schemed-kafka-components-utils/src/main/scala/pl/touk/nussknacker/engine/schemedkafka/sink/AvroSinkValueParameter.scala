package pl.touk.nussknacker.engine.schemedkafka.sink

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, ValidatedNel}
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.CustomNodeError
import pl.touk.nussknacker.engine.api.definition.Parameter
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer.{SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, SinkValueParamName, TopicParamName}
import pl.touk.nussknacker.engine.schemedkafka.AvroDefaultExpressionDeterminer
import pl.touk.nussknacker.engine.schemedkafka.typed.AvroSchemaTypeDefinitionExtractor
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.util.sinkvalue.SinkValueData.{SchemaOutputValidator, SinkRecordParameter, SinkSingleValueParameter, SinkValueParameter}

import scala.collection.immutable.ListMap

object AvroSinkValueParameter {
  import scala.jdk.CollectionConverters._

  type FieldName = String

  val restrictedParamNames: Set[FieldName] =
    Set(SchemaVersionParamName, SinkKeyParamName, SinkValidationModeParameterName, TopicParamName)

  /*
    We extract editor form from Avro schema
   */
  def apply(schema: Schema)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] =
    apply(schema, path = Nil, defaultValue = None)

  def apply(schema: Schema, path: List[String], defaultValue: Option[Expression])
           (implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, SinkValueParameter] = {
    import cats.implicits.{catsStdInstancesForList, toTraverseOps}

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
          val newPath = fieldName :: path
          val sinkValueValidated = getDefaultValue(recordField, newPath).andThen { defaultValue =>
            AvroSinkValueParameter(schema = recordField.schema(), path = newPath, defaultValue)
          }
          sinkValueValidated.map(sinkValueParam => fieldName -> sinkValueParam)
        }
        listOfValidatedParams.sequence.map(l => ListMap(l: _*)).map(SinkRecordParameter(path, _))
      }
    } else {
      Valid(AvroSinkSingleValueParameter(Option(path).filter(_.nonEmpty).getOrElse(List(SinkValueParamName)), schema, defaultValue))
    }
  }

  private def getDefaultValue(fieldSchema: Schema.Field, path: List[String])(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, Option[Expression]] =
    new AvroDefaultExpressionDeterminer(handleNotSupported = true).determine(fieldSchema)
      .leftMap(_.map(err => CustomNodeError(err.getMessage, Some(path.reverse.mkString(".")))))

  private def containsRestrictedNames(fields: List[Schema.Field]): Boolean = {
    val fieldNames = fields.map(_.name()).toSet
    fieldNames.nonEmpty & (fieldNames & restrictedParamNames).nonEmpty
  }

}

object AvroSinkSingleValueParameter {

  def apply(path: List[String], schema: Schema, defaultValue: Option[Expression]): SinkSingleValueParameter = {
    val typing = AvroSchemaTypeDefinitionExtractor.typeDefinition(schema)
    val name = path.reverse.mkString(".")
    val parameter = (
      if (schema.isNullable) Parameter.optional(name, typing) else Parameter(name, typing)
      ).copy(
      isLazyParameter = true,
      defaultValue = defaultValue.map(_.expression)
    )
    //for now we don't SchemaOutputValidator for avro in editor mode
    SinkSingleValueParameter(path, parameter, SchemaOutputValidator.emptyValidator)
  }
}
