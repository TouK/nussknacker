package pl.touk.nussknacker.engine.schemedkafka.kryo

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.java.typeutils.AvroUtils
import pl.touk.nussknacker.engine.api.component.ComponentProviderConfig
import pl.touk.nussknacker.engine.flink.api.serialization.SerializersRegistrar
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.SchemaRegistryClientFactory
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.kryo.SchemaIdBasedAvroGenericRecordSerializer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.GenericRecordSchemaIdSerializationSupport
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaRegistryClientFactory

// We need it because we use avro records inside our Context class
class AvroSerializersRegistrar extends SerializersRegistrar with LazyLogging {

  override def register(modelConfig: Config, executionConfig: ExecutionConfig): Unit = {
    logger.debug("Registering default avro serializers")
    registerAvroSerializers(executionConfig)
    val resolvedKafkaConfig = resolveConfig(modelConfig)
    registerGenericRecordSchemaIdSerializationForGlobalKafkaConfigIfNeed(resolvedKafkaConfig, executionConfig)
  }

  // protected for overriding for compatibility with Flink < v.1.19
  protected def registerAvroSerializers(executionConfig: ExecutionConfig): Unit = {
    AvroUtils.getAvroUtils.addAvroSerializersIfRequired(
      executionConfig.getSerializerConfig,
      classOf[GenericData.Record]
    )
  }

  private def resolveConfig(modelConfig: Config): Option[KafkaConfig] = {
    val componentsConfig = modelConfig.getAs[Map[String, ComponentProviderConfig]]("components").getOrElse(Map.empty)
    val componentsKafkaConfigs = componentsConfig.toList
      .filter { case (name, config) =>
        val providerType = config.providerType.getOrElse(name)
        providerType == "kafka"
      }
      .filterNot(_._2.disabled)
      .map { case (name, config) =>
        name -> KafkaConfig.parseConfig(config.config, "config")
      }
    componentsKafkaConfigs match {
      case (componentName, kafkaConfig) :: Nil =>
        logger.debug(s"Found one enabled kafka component: $componentName")
        Some(kafkaConfig)
      case Nil =>
        val configOpt = KafkaConfig.parseConfigOpt(modelConfig)
        configOpt.foreach(_ => logger.debug(s"No kafka components found, but model root kafka config found"))
        configOpt
      case _ => None // mechanism would be disabled in case if there is more than one kafka component enabled
    }
  }

  // It registers GenericRecordSchemaIdSerialization only for first kafka component config
  // If you want to register GenericRecordSchemaIdSerialization for other kafka config you need to invoke
  // `AvroSerializersRegistrar.registerGenericRecordSchemaIdSerializationIfNeed` directly
  private def registerGenericRecordSchemaIdSerializationForGlobalKafkaConfigIfNeed(
      componentKafkaConfig: Option[KafkaConfig],
      executionConfig: ExecutionConfig
  ): Unit = {
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
    registerGenericRecordSchemaIdSerializationIfNeed(config, UniversalSchemaRegistryClientFactory, kafkaConfig)
  }

  def registerGenericRecordSchemaIdSerializationIfNeed(
      config: ExecutionConfig,
      schemaRegistryClientFactory: SchemaRegistryClientFactory,
      kafkaConfig: KafkaConfig
  ): Unit = {
    if (GenericRecordSchemaIdSerializationSupport.schemaIdSerializationEnabled(kafkaConfig)) {
      logger.debug("Registering SchemaIdBasedAvroGenericRecordSerializer")
      SchemaIdBasedAvroGenericRecordSerializer.registrar(schemaRegistryClientFactory, kafkaConfig).registerIn(config)
    } else {
      logger.debug("Skipping SchemaIdBasedAvroGenericRecordSerializer registration")
    }
  }

}
