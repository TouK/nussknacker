package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.google.common.annotations.VisibleForTesting
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.serialization.SerializerConfigImpl
import org.apache.flink.api.java.typeutils.AvroUtils
import pl.touk.nussknacker.engine.api.component.ComponentProviderConfig
import pl.touk.nussknacker.engine.flink.api.serialization.SerializersRegistrar
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{GenericRecordWithSchemaId, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaRegistryClientFactory

class AvroSerializersRegistrar extends SerializersRegistrar with LazyLogging {

  override def register(modelConfig: Config, executionConfig: ExecutionConfig): Unit = {
    logger.debug("Registering custom Avro serializers")

    val serializerImpl = executionConfig.getSerializerConfig.asInstanceOf[SerializerConfigImpl]
    registerGenericSerializer(serializerImpl)
    registerOptimizedSerializers(serializerImpl, modelConfig)
  }

  /**
   * Registers default serializer that was used before Flink 2.0 - we need this because the new one from
   * Flink's AvroUtils uses a plain GenericDatumWriter that ignores our extended logical type conversions
   */
  private def registerGenericSerializer(serializerConfig: SerializerConfigImpl): Unit = {
    val genericRecordClass = classOf[GenericData.Record]
    AvroUtils.getAvroUtils.addAvroSerializersIfRequired(serializerConfig, genericRecordClass)
    if (serializerConfig.getRegisteredTypesWithKryoSerializerClasses.get(genericRecordClass) == null) {
      throw new RuntimeException(
        s"Serializer registration for $genericRecordClass not found, override code needs to be updated"
      )
    }
    serializerConfig.registerTypeWithKryoSerializer(genericRecordClass, classOf[FieldSerializer[_]])
  }

  /**
   * Registers optimized [[GenericRecordWithSchemaId]] serializers for `kafka` components.
   *
   * If you want to register a serializer for other Kafka components you need to invoke
   * [[AvroSerializersRegistrar#registerGenericRecordSchemaIdSerialization]] directly.
   */
  private def registerOptimizedSerializers(serializerConfig: SerializerConfigImpl, modelConfig: Config): Unit = {
    serializerConfig.registerTypeWithKryoSerializer(
      classOf[GenericRecordWithSchemaId],
      classOf[GenericRecordWithSchemaIdSerializer]
    )

    resolveKafkaComponentsConfigs(modelConfig).foreach { case (componentName, resolvedKafkaConfig) =>
      val autoRegister = resolvedKafkaConfig.kafkaEspProperties
        .flatMap(_.get(AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty).map(_.toBoolean))
        .getOrElse(true)
      if (autoRegister) {
        AvroSerializersRegistrar.registerGenericRecordSchemaIdSerialization(
          UniversalSchemaRegistryClientFactory,
          resolvedKafkaConfig,
          Some(componentName)
        )
      } else {
        logger.debug(
          s"Auto registration of ${classOf[GenericRecordWithSchemaIdSerializer].getSimpleName} for $componentName " +
            s"is disabled by ${AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty} configuration property"
        )
      }
    }
  }

  private def resolveKafkaComponentsConfigs(modelConfig: Config): List[(String, KafkaComponentsConfig)] = {
    modelConfig
      .getAs[Map[String, ComponentProviderConfig]]("components")
      .getOrElse(Map.empty)
      .toList
      .filter { case (name, config) =>
        val providerName = config.providerType.getOrElse(name)
        providerName == "kafka"
      }
      .filterNot { case (_, config) => config.disabled }
      .map { case (name, config) =>
        name -> KafkaComponentsConfig.parseConfigNestedAtConfigKey(config.config)
      }
  }

}

object AvroSerializersRegistrar extends LazyLogging {

  // This property is mainly for disabling of automatic registration in tests
  val autoRegisterRecordSchemaIdSerializationProperty = "autoRegisterRecordSchemaIdSerialization"

  def registerGenericRecordSchemaIdSerialization(
      schemaRegistryClientFactory: SchemaRegistryClientFactory,
      kafkaComponentsConfig: KafkaComponentsConfig,
      componentName: Option[String] = None
  ): Unit = {
    val componentIdentifier = componentName.getOrElse(
      s"Kafka component with boostrap.servers [${kafkaComponentsConfig.kafkaBootstrapServers}]" +
        s"and schema.registry.url [${kafkaComponentsConfig.kafkaProperties.getOrElse("schema.registry.url", "-")}]"
    )
    if (kafkaComponentsConfig.optimizedGenericRecordSerialization.enabled) {
      val schemaRegistryUrl = kafkaComponentsConfig.kafkaProperties("schema.registry.url")
      val schemaRegistryId =
        kafkaComponentsConfig.optimizedGenericRecordSerialization.toValidConfig(schemaRegistryUrl).schemaRegistryId
      logger.debug(
        s"Registering ${classOf[GenericRecordWithSchemaIdSerializer].getSimpleName}[$schemaRegistryId] " +
          s"using Schema Registry client factory [$schemaRegistryClientFactory] for $componentIdentifier"
      )
      GenericRecordWithSchemaIdSerializer.register(
        schemaRegistryId,
        schemaRegistryClientFactory.create(kafkaComponentsConfig.schemaRegistryClientKafkaConfig),
      )
    } else {
      logger.debug(
        s"Registration of ${classOf[GenericRecordWithSchemaIdSerializer].getSimpleName} for $componentIdentifier " +
          s"is disabled by component configuration"
      )
    }
  }

  /**
   * Registrations do not need to be cleared as [[GenericRecordWithSchemaIdSerializer]] lifetime
   * is the same as the model it's in.
   * This method is meant to be used in tests, to prevent us from cross-test contamination. It should be used
   * in all test classes that call [[registerGenericRecordSchemaIdSerialization]].
   */
  @VisibleForTesting
  def clearRegistrations(): Unit = {
    GenericRecordWithSchemaIdSerializer.clearRegistrations()
  }

}
