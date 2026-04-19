package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.flink

import com.esotericsoftware.kryo.serializers.FieldSerializer
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import org.apache.avro.generic.GenericData
import org.apache.flink.api.common.{ExecutionConfig, SerializableSerializer}
import org.apache.flink.api.common.serialization.{SerializerConfig, SerializerConfigImpl}
import org.apache.flink.api.java.typeutils.AvroUtils
import pl.touk.nussknacker.engine.api.component.ComponentProviderConfig
import pl.touk.nussknacker.engine.flink.api.serialization.SerializersRegistrar
import pl.touk.nussknacker.engine.kafka.{KafkaComponentsConfig, SchemaRegistryClientKafkaConfig}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.{GenericRecordWithSchemaId, SchemaRegistryClientFactory}
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.serialization.GenericRecordSchemaIdSerializationSupport
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.universal.UniversalSchemaRegistryClientFactory

import scala.collection.mutable

class AvroSerializersRegistrar extends SerializersRegistrar with LazyLogging {

  /**
   * Registers serializers for [[GenericRecordWithSchemaId]]:
   * <ul>
   *   <li>a generic Flink serializer with support for logical types</li>
   *   <li>an optimized serializer for detected `kafka` components.</li>
   * </ul>
   *
   * If you want to register an optimized serializer for other Kafka components you need to invoke
   * [[AvroSerializersRegistrar#addOptimizedSerializer]].
   */
  override def register(modelConfig: Config, executionConfig: ExecutionConfig): Unit = {
    logger.debug("Registering Avro serializers")
    val serializerConfigImpl = castSerializerConfig(executionConfig.getSerializerConfig)
    registerGenericSerializer(serializerConfigImpl)
    validateIdsAndRegisterOptimizedSerializer(
      serializerConfigImpl,
      UniversalSchemaRegistryClientFactory,
      resolveKafkaComponentsConfigs(modelConfig)
    )
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

  private def validateIdsAndRegisterOptimizedSerializer(
      serializerConfig: SerializerConfig,
      schemaRegistryClientFactory: SchemaRegistryClientFactory,
      KafkaComponentsConfig: List[(Option[String], KafkaComponentsConfig)]
  ): Unit = {
    val seenAutoIds = mutable.Map[Int, String]()
    val schemaRegistryConfigs = KafkaComponentsConfig.map { case (componentNameOpt, config) =>
      val schemaRegistryId = config.optimizedGenericRecordSerialization.schemaRegistryId match {
        case Some(explicitId) => explicitId
        case None =>
          val autoId = extractSchemaRegistryId(config)
          val componentName = componentNameOpt
            .getOrElse { s"[schema.registry.url=${config.kafkaProperties("schema.registry.url")}]" }
          seenAutoIds.get(autoId) match {
            case None       => seenAutoIds.put(autoId, componentName)
            case Some(name) =>
              // this can work correctly if both refer the same Schema Registry URL,
              // but identifiers should be assigned explicitly anyway
              throw new IllegalArgumentException(
                s"Multiple components resolve to the same automatically assigned schemaRegistryId: $name, $componentName. " +
                  s"Please assign explicit identifiers."
              )
          }
          autoId
      }
      schemaRegistryId -> config.schemaRegistryClientKafkaConfig
    }.toMap

    registerOptimizedSerializer(serializerConfig, schemaRegistryClientFactory, schemaRegistryConfigs)
  }

  def registerOptimizedSerializer(
      serializerConfig: SerializerConfig,
      schemaRegistryClientFactory: SchemaRegistryClientFactory,
      KafkaComponentsConfig: List[KafkaComponentsConfig]
  ): Unit = {
    validateIdsAndRegisterOptimizedSerializer(
      serializerConfig,
      schemaRegistryClientFactory,
      KafkaComponentsConfig.map { (None, _) }
    )
  }

  def registerOptimizedSerializer(
      serializerConfig: SerializerConfig,
      schemaRegistryClientFactory: SchemaRegistryClientFactory,
      schemaRegistryConfigs: Map[Int, SchemaRegistryClientKafkaConfig]
  ): Unit = {
    // TODO: We shouldn't use instance-base serializers registration here.
    //       Instance-based Kryo serializers registration is deprecated and may be be removed in a future Flink version.
    //       We should use standard TypeInfo/TypeInfoFactory or class-based Kryo serializers,
    //       but currently there is no possibility of having them parameterized with Schema Registry addresses
    //       or making them stateful (to remember seen schemas so that they can be serialized only once).
    //       .
    //       This also causes issues with RawType usage in table-api components because, RawTypes become incomparable
    //       if there is any instance-based serializer registered in ExecutionConfig:
    //         - RawType.equals checks serializer.equals(rawType.serializer)
    //         - KryoSerializer.equals checks Objects.equals(defaultSerializers, other.defaultSerializers)
    //         - KryoSerializer.defaultSerializers is a LinkedHashMap<Class<?>, ExecutionConfig.SerializableSerializer<?>>
    //         - SerializableSerializer has equals method not implemented (so it checks reference equality)

    val serializerConfigImpl = castSerializerConfig(serializerConfig)
    // TODO: try to subclass SerializableSerializer?
    val serializer = new GenericRecordWithSchemaIdSerializer(
      schemaRegistryClientFactory,
      Map.empty[Int, SchemaRegistryClientKafkaConfig]
    )
    // register for exact type, gets a fixed registration ID
    serializerConfigImpl.getRegisteredTypesWithKryoSerializers.put(
      classOf[GenericRecordWithSchemaId],
      new SerializableSerializer(serializer)
    )

    // TODO: do we need this?
    // register for type and its subtypes, doesn't get a registration ID
    serializerConfigImpl.getDefaultKryoSerializers.put(
      classOf[GenericRecordWithSchemaId],
      new SerializableSerializer(serializer)
    )

    // this ensures that our API for adding a serializer actually works
    schemaRegistryConfigs.foreach { case (schemaRegistryId, schemaRegistryConfig) =>
      addOptimizedSerializer(serializerConfig, schemaRegistryId, schemaRegistryConfig)
    }
  }

  def addOptimizedSerializer(
      serializerConfig: SerializerConfig,
      schemaRegistryId: Int,
      schemaRegistryConfig: SchemaRegistryClientKafkaConfig
  ): Unit = {
    val serializerConfigImpl = castSerializerConfig(serializerConfig)
    val serializer1 = serializerConfigImpl.getRegisteredTypesWithKryoSerializers.get(classOf[GenericRecordWithSchemaId])
    val serializer2 = serializerConfigImpl.getDefaultKryoSerializers.get(classOf[GenericRecordWithSchemaId])
    if (serializer1 == null || serializer2 == null) {
      throw new IllegalStateException("Serializer must be initialized by calling registerOptimizedSerializer method")
    }
    if (!serializer1.getSerializer.asInstanceOf[AnyRef].eq(serializer2.getSerializer)) {
      throw new IllegalArgumentException("Registered type and subtype serializers are not equal")
    }

    serializer1.getSerializer
      .asInstanceOf[GenericRecordWithSchemaIdSerializer]
      .registerSchemaRegistry(
        schemaRegistryId,
        schemaRegistryConfig
      )
  }

  private def castSerializerConfig(serializerConfig: SerializerConfig): SerializerConfigImpl = serializerConfig match {
    case impl: SerializerConfigImpl => impl
    case _ =>
      throw new IllegalArgumentException(
        s"Passed SerializerConfig must be of type ${classOf[SerializerConfigImpl].getName} but was ${serializerConfig.getClass.getName}"
      )
  }

  private def resolveKafkaComponentsConfigs(modelConfig: Config): List[(Option[String], KafkaComponentsConfig)] = {
    modelConfig
      .getAs[Map[String, ComponentProviderConfig]]("components")
      .getOrElse(Map.empty)
      .toList
      .filter { case (name, config) =>
        val providerName = config.providerType.getOrElse(name)
        providerName == "kafka" && !config.disabled
      }
      .map { case (name, config) =>
        name -> KafkaComponentsConfig.parseConfigNestedAtConfigKey(config.config)
      }
      .filter { case (_, config) =>
        GenericRecordSchemaIdSerializationSupport.isEnabledForComponent(config)
      }
      .filter { case (componentName, config) =>
        val autoRegister = config.kafkaEspProperties
          .flatMap(_.get(AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty).map(_.toBoolean))
          .getOrElse(true)
        if (!autoRegister) {
          logger.debug(
            s"Auto registration of ${classOf[GenericRecordWithSchemaIdSerializer].getSimpleName} for $componentName " +
              s"is disabled by ${AvroSerializersRegistrar.autoRegisterRecordSchemaIdSerializationProperty} configuration property"
          )
        }
        autoRegister
      }
      .map { case (componentName, config) => (Some(componentName), config) }
  }

  private def extractSchemaRegistryId(config: KafkaComponentsConfig): Int = {
    val schemaRegistryUrl = config.kafkaProperties("schema.registry.url")
    config.optimizedGenericRecordSerialization.schemaRegistryId match {
      case Some(explicitId) => explicitId
      case None => config.optimizedGenericRecordSerialization.toValidConfig(schemaRegistryUrl).schemaRegistryId
    }
  }

}

object AvroSerializersRegistrar {
  // Use to disable automatic registration, e.g. so that tests can register their mocked Schema Registry clients
  val autoRegisterRecordSchemaIdSerializationProperty = "autoRegisterRecordSchemaIdSerialization"
}
