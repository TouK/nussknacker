package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization

import com.typesafe.scalalogging.LazyLogging
import io.confluent.kafka.schemaregistry.avro.AvroSchema
import org.apache.avro.generic.{GenericData, GenericRecord}
import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedObjectTypingResult, TypingResult}
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.schemedkafka.RuntimeSchemaData
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.GenericRecordWithSchemaId

trait GenericRecordSchemaIdSerializationSupport {
  def wrapRecordIfNeeded(data: AnyRef, readerSchemaData: RuntimeSchemaData[AvroSchema]): AnyRef

  def wrapRecordTypeIfNeeded(typingResult: TypingResult): TypingResult
}

object GenericRecordSchemaIdSerializationSupport extends LazyLogging {

  def apply(kafkaComponentsConfig: KafkaComponentsConfig): GenericRecordSchemaIdSerializationSupport = {
    if (isEnabledForComponent(kafkaComponentsConfig)) {
      val schemaRegistryUrl = kafkaComponentsConfig.kafkaProperties("schema.registry.url")
      val fullConfig        = kafkaComponentsConfig.optimizedGenericRecordSerialization.toValidConfig(schemaRegistryUrl)
      new Wrapping(fullConfig.schemaRegistryId)
    } else {
      new Identity()
    }
  }

  private def isEnabledForComponent(kafkaComponentsConfig: KafkaComponentsConfig): Boolean =
    !kafkaComponentsConfig.avroAsJsonSerialization.getOrElse(false) &&
      kafkaComponentsConfig.optimizedGenericRecordSerialization.enabled &&
      kafkaComponentsConfig.kafkaProperties.contains("schema.registry.url")

  // noinspection ScalaWeakerAccess
  class Wrapping(schemaRegistryId: Int) extends GenericRecordSchemaIdSerializationSupport {

    override def wrapRecordIfNeeded(
        data: AnyRef,
        readerSchemaData: RuntimeSchemaData[AvroSchema]
    ): AnyRef = {
      data match {
        case genericRecord: GenericData.Record =>
          val readerSchemaId = readerSchemaData.schemaIdOpt.getOrElse(
            throw new IllegalStateException(
              "SchemaId serialization enabled but schemaId is missing in reader schema data"
            )
          )
          new GenericRecordWithSchemaId(genericRecord, schemaRegistryId, readerSchemaId, false)
        case _ => data
      }
    }

    override def wrapRecordTypeIfNeeded(typingResult: TypingResult): TypingResult = typingResult match {
      case t: TypedObjectTypingResult if t.runtimeObjType.klass == classOf[GenericRecord] =>
        Typed.record(t.fields, Typed.typedClass[GenericRecordWithSchemaId], t.additionalInfo)
      case _ => typingResult
    }

  }

  // noinspection ScalaWeakerAccess
  class Identity extends GenericRecordSchemaIdSerializationSupport {

    override def wrapRecordIfNeeded(data: AnyRef, readerSchemaData: RuntimeSchemaData[AvroSchema]): AnyRef = data

    override def wrapRecordTypeIfNeeded(typingResult: TypingResult): TypingResult = typingResult

  }

}
