package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.client

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import com.azure.data.schemaregistry.models.SchemaProperties
import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.ParsedSchema
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.Schema
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{
  SchemaId,
  SchemaRegistryClientWithRegistration,
  SchemaRegistryError,
  SchemaTopicError
}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure.SchemaNameTopicMatchStrategy

trait AzureSchemaRegistryClient extends SchemaRegistryClientWithRegistration with LazyLogging {

  protected def checkAvroSchema(schema: ParsedSchema): AvroSchema = {
    schema match {
      case avroSchema: AvroSchema => avroSchema
      case _ => throw new IllegalArgumentException("Currently only avro schema is supported for Azure implementation")
    }
  }

  protected def getOneMatchingSchemaName(
      topicName: UnspecializedTopicName,
      isKey: Boolean
  ): Validated[SchemaRegistryError, String] = {
    getAllFullSchemaNames.andThen { fullSchemaNames =>
      val matchingFullSchemaNames = SchemaNameTopicMatchStrategy.getMatchingSchemas(topicName, fullSchemaNames, isKey)
      matchingFullSchemaNames match {
        case one :: Nil =>
          Valid(one)
        case Nil =>
          Invalid(SchemaTopicError(s"Schema for topic: $topicName not found"))
        case moreThenOnce =>
          // We can't pick one in this case because there is no option to recognize which one is newer.
          // I've tried to parse schemaId to UUID but it is saved in Version 4 UUID format (random) and has no timestamp
          Invalid(SchemaTopicError(s"Ambiguous schemas: ${moreThenOnce.mkString(", ")} for topic: $topicName"))
      }
    }
  }

  protected def getAllFullSchemaNames: Validated[SchemaRegistryError, List[String]]

  override def registerSchema(topicName: UnspecializedTopicName, isKey: Boolean, schema: ParsedSchema): SchemaId = {
    val schemaNameBasedOnTopic = SchemaNameTopicMatchStrategy.schemaNameFromTopicName(topicName, isKey)
    val avroSchema             = checkAvroSchema(schema).rawSchema()
    if (avroSchema.getType == Schema.Type.RECORD) {
      require(
        avroSchema.getName == schemaNameBasedOnTopic,
        s"Invalid record schema name ${avroSchema.getName} Should be: $schemaNameBasedOnTopic."
      )
    } else {
      // for primitive types we have to register schema on two names - one for listing of topics purpose and second one based on name, to be possible to serialize such object
      // by KafkaAvroSerializer - it just lookup into schema registry based on schema's full name. Can be tricky which one id is returned - in most cases it easy better
      // to return this based on name
      registerSchemaVersionIfNotExists(schema, Some(schemaNameBasedOnTopic))
    }
    SchemaId.fromString(registerSchemaVersionIfNotExists(schema).getId)
  }

  // forceSchemaNameOpt is for special purposes like primitive schemas when there is no name
  def registerSchemaVersionIfNotExists(
      schema: ParsedSchema,
      forceSchemaNameOpt: Option[String] = None
  ): SchemaProperties

  def getSchemaIdByContent(schema: AvroSchema): SchemaId

}
