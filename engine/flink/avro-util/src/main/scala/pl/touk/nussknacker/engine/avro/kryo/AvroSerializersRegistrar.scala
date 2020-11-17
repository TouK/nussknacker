package pl.touk.nussknacker.engine.avro.kryo

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.AvroUtils
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.kryo.SchemaIdBasedAvroGenericRecordSerializer
import pl.touk.nussknacker.engine.flink.api.serialization.SerializersRegistrar
import pl.touk.nussknacker.engine.kafka.KafkaConfig

// We need it because we use avro records inside our Context class
class AvroSerializersRegistrar extends SerializersRegistrar with LazyLogging {

  override def register(modelConfig: Config, executionConfig: ExecutionConfig): Unit = {
    logger.debug("Registering default avro serializers")
    AvroUtils.getAvroUtils.addAvroSerializersIfRequired(executionConfig, classOf[GenericData.Record])
    registerGenericRecordSchemaIdSerializationForGlobalKafkaConfigIfNeed(modelConfig, executionConfig)
  }

  // It registers GenericRecordSchemaIdSerialization only for global kafka config (in 'kafka' path)
  // If you want to register GenericRecordSchemaIdSerialization for other kafka config you need to invoke
  // `AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed` directly
  private def registerGenericRecordSchemaIdSerializationForGlobalKafkaConfigIfNeed(modelConfig: Config, executionConfig: ExecutionConfig): Unit = {
    KafkaConfig.parseConfigOpt(modelConfig).foreach { globalKafkaConfig =>
      val autoRegister = globalKafkaConfig.kafkaEspProperties
        .flatMap(_.get(AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty).map(_.toBoolean))
        .getOrElse(true)
      if (autoRegister) {
        AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed(executionConfig, globalKafkaConfig)
      }
    }
  }

}

object AvroSerializersRegistrar extends LazyLogging{

  // This property is mainly for testing purpose. You can use it to skip GenericRecordSchemaIdSerialization registration
  val autoRegisterRecordSchemaIdSerializationProperty = "autoRegisterRecordSchemaIdSerialization"

  def registerGenericRecordSchemaIdSerializationIfNeed(config: ExecutionConfig, kafkaConfig: KafkaConfig): Unit = {
    registerGenericRecordSchemaIdSerializationIfNeed(config, CachedConfluentSchemaRegistryClientFactory(), kafkaConfig)
  }

  def registerGenericRecordSchemaIdSerializationIfNeed(config: ExecutionConfig, schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, kafkaConfig: KafkaConfig): Unit = {
    if (KryoGenericRecordSchemaIdSerializationSupport.schemaIdSerializationEnabled(kafkaConfig)) {
      logger.debug("Registering SchemaIdBasedAvroGenericRecordSerializer")
      new SchemaIdBasedAvroGenericRecordSerializer(schemaRegistryClientFactory, kafkaConfig).registerIn(config)
    } else {
      logger.debug("Skipping SchemaIdBasedAvroGenericRecordSerializer registration")
    }
  }

}
