package pl.touk.nussknacker.engine.avro.kryo

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus.{toFicusConfig, _}
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.AvroUtils
import pl.touk.nussknacker.engine.api.component.ComponentProviderConfig
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.client.{CachedConfluentSchemaRegistryClientFactory, ConfluentSchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.kryo.SchemaIdBasedAvroGenericRecordSerializer
import pl.touk.nussknacker.engine.avro.schemaregistry.confluent.serialization.GenericRecordSchemaIdSerializationSupport
import pl.touk.nussknacker.engine.flink.api.serialization.SerializersRegistrar
import pl.touk.nussknacker.engine.kafka.KafkaConfig

// We need it because we use avro records inside our Context class
class AvroSerializersRegistrar extends SerializersRegistrar with LazyLogging {

  override def register(modelConfig: Config, executionConfig: ExecutionConfig): Unit = {
    logger.debug("Registering default avro serializers")
    val componentsConfig = modelConfig.getAs[Map[String, ComponentProviderConfig]]("components").getOrElse(Map.empty)
    val componentKafkaConfig = componentsConfig
      .filterNot(_._2.disabled)
      .map(_._2.config)
      .flatMap(KafkaConfig.parseConfigOpt(_, "config.kafka"))
      .headOption
      .orElse(KafkaConfig.parseConfigOpt(modelConfig))

    AvroUtils.getAvroUtils.addAvroSerializersIfRequired(executionConfig, classOf[GenericData.Record])
    registerGenericRecordSchemaIdSerializationForGlobalKafkaConfigIfNeed(componentKafkaConfig, executionConfig)
  }

  // It registers GenericRecordSchemaIdSerialization only for first kafka component config
  // If you want to register GenericRecordSchemaIdSerialization for other kafka config you need to invoke
  // `AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed` directly
  private def registerGenericRecordSchemaIdSerializationForGlobalKafkaConfigIfNeed(componentKafkaConfig: Option[KafkaConfig], executionConfig: ExecutionConfig): Unit = {
    componentKafkaConfig.foreach { kafkaConfig =>
      val autoRegister = kafkaConfig.kafkaEspProperties
        .flatMap(_.get(AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty).map(_.toBoolean))
        .getOrElse(true)
      logger.debug(s"Auto registering SchemaIdBasedAvroGenericRecordSerializer: $autoRegister")
      if (autoRegister) {
        AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed(executionConfig, kafkaConfig)
      }
    }
  }

}

object AvroSerializersRegistrar extends LazyLogging {

  // This property is mainly for testing purpose. You can use it to skip GenericRecordSchemaIdSerialization registration
  val autoRegisterRecordSchemaIdSerializationProperty = "autoRegisterRecordSchemaIdSerialization"

  def registerGenericRecordSchemaIdSerializationIfNeed(config: ExecutionConfig, kafkaConfig: KafkaConfig): Unit = {
    registerGenericRecordSchemaIdSerializationIfNeed(config, CachedConfluentSchemaRegistryClientFactory(), kafkaConfig)
  }

  def registerGenericRecordSchemaIdSerializationIfNeed(config: ExecutionConfig, schemaRegistryClientFactory: ConfluentSchemaRegistryClientFactory, kafkaConfig: KafkaConfig): Unit = {
    if (GenericRecordSchemaIdSerializationSupport.schemaIdSerializationEnabled(kafkaConfig)) {
      logger.debug("Registering SchemaIdBasedAvroGenericRecordSerializer")
      new SchemaIdBasedAvroGenericRecordSerializer(schemaRegistryClientFactory, kafkaConfig).registerIn(config)
    } else {
      logger.debug("Skipping SchemaIdBasedAvroGenericRecordSerializer registration")
    }
  }

}
