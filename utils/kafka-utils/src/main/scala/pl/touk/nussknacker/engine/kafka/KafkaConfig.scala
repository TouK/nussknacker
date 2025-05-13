package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.Config
import enumeratum.{Enum, EnumEntry}
import pl.touk.nussknacker.engine.kafka.IdlenessConfig.DefaultDuration
import pl.touk.nussknacker.engine.kafka.KafkaConfig._

import scala.collection.immutable
import scala.concurrent.duration._

case class SchemaRegistryClientKafkaConfig(
    kafkaProperties: Map[String, String],
    cacheConfig: SchemaRegistryCacheConfig,
    avroAsJsonSerialization: Option[Boolean]
)

case class KafkaConfig(
    kafkaProperties: Option[Map[String, String]],
    kafkaEspProperties: Option[Map[String, String]],
    consumerGroupNamingStrategy: Option[ConsumerGroupNamingStrategy.Value] = None,
    // Probably better place for this flag would be configParameters inside global parameters but
    // for easier usage in AbstractConfluentKafkaAvroDeserializer and ConfluentKafkaAvroDeserializerFactory it is placed here
    // TODO rename to avroGenericRecordSchemaIdSerialization
    avroKryoGenericRecordSchemaIdSerialization: Option[Boolean] = None,
    topicsExistenceValidationConfig: TopicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = true),
    // By default we want to handle keys as ordinary String. For specific scenario,
    // when complex key with its own schema is provided, this flag is false
    // and all topics related to this config require both key and value schema definitions.
    useStringForKey: Boolean = true,
    schemaRegistryCacheConfig: SchemaRegistryCacheConfig = SchemaRegistryCacheConfig(),
    avroAsJsonSerialization: Option[Boolean] = None,
    kafkaAddress: Option[String] = None,
    idleTimeout: Option[IdlenessConfig] = None,
    sinkDeliveryGuarantee: Option[SinkDeliveryGuarantee.Value] = None,
    showTopicsWithoutSchema: Boolean = true,
    topicsWithoutSchemaFetchTimeout: FiniteDuration = 1 second
) {

  def schemaRegistryClientKafkaConfig = SchemaRegistryClientKafkaConfig(
    kafkaProperties.getOrElse(Map.empty),
    schemaRegistryCacheConfig,
    avroAsJsonSerialization
  )

  def defaultOffsetResetStrategy: Option[OffsetResetStrategy] =
    kafkaEspProperties.flatMap(_.get(DefaultOffsetResetStrategyPath)).map(OffsetResetStrategy.withName)

  def defaultMaxOutOfOrdernessMillis: java.time.Duration =
    kafkaEspProperties
      .flatMap(_.get(DefaultMaxOutOfOrdernessMillisPath))
      .map(max => java.time.Duration.ofMillis(max.toLong))
      .getOrElse(DefaultMaxOutOfOrdernessMillisDefault)

  def idleTimeoutDuration: Option[java.time.Duration] = {
    val finalIdleConfig = idleTimeout.getOrElse(IdlenessConfig.DefaultConfig)
    if (finalIdleConfig.enabled) {
      Some(java.time.Duration.ofMillis(finalIdleConfig.duration.toMillis))
    } else {
      None
    }
  }

  def kafkaBootstrapServers: Option[String] = kafkaProperties
    .getOrElse(Map.empty)
    .get("bootstrap.servers")
    .orElse(kafkaAddress)

}

object ConsumerGroupNamingStrategy extends Enumeration {
  // TODO: Rename to processName and processName-nodeName
  val ProcessId: ConsumerGroupNamingStrategy.Value       = Value("processId")
  val ProcessIdNodeId: ConsumerGroupNamingStrategy.Value = Value("processId-nodeId")
}

object KafkaConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  val DefaultGlobalKafkaConfigPath                              = "kafka"
  val DefaultOffsetResetStrategyPath                            = "defaultOffsetResetStrategy"
  val DefaultMaxOutOfOrdernessMillisPath                        = "defaultMaxOutOfOrdernessMillis"
  val DefaultMaxOutOfOrdernessMillisDefault: java.time.Duration = java.time.Duration.ofMillis(60000)

  def parseConfigOpt(config: Config, path: String = DefaultGlobalKafkaConfigPath): Option[KafkaConfig] = {
    config.getAs[KafkaConfig](path)
  }

  def parseConfig(config: Config, path: String = DefaultGlobalKafkaConfigPath): KafkaConfig = {
    config.as[KafkaConfig](path)
  }

}

final case class TopicsExistenceValidationConfig(
    enabled: Boolean,
    validatorConfig: CachedTopicsExistenceValidatorConfig = CachedTopicsExistenceValidatorConfig.DefaultConfig
)

final case class CachedTopicsExistenceValidatorConfig(
    autoCreateFlagFetchCacheTtl: FiniteDuration,
    topicsFetchCacheTtl: FiniteDuration,
    adminClientTimeout: FiniteDuration
)

object CachedTopicsExistenceValidatorConfig {

  val DefaultConfig: CachedTopicsExistenceValidatorConfig = CachedTopicsExistenceValidatorConfig(
    autoCreateFlagFetchCacheTtl = 5 minutes,
    topicsFetchCacheTtl = 30 seconds,
    adminClientTimeout = 10 seconds
  )

}

case class IdlenessConfig(enabled: Boolean, duration: FiniteDuration = DefaultDuration)

object IdlenessConfig {
  val DefaultDuration: FiniteDuration = 3 minutes
  val DefaultConfig: IdlenessConfig   = IdlenessConfig(enabled = true, duration = DefaultDuration)
}

object SinkDeliveryGuarantee extends Enumeration {
  val ExactlyOnce: SinkDeliveryGuarantee.Value = Value("EXACTLY_ONCE")
  val AtLeastOnce: SinkDeliveryGuarantee.Value = Value("AT_LEAST_ONCE")
  val None: SinkDeliveryGuarantee.Value        = Value("NONE")
}

sealed trait OffsetResetStrategy extends EnumEntry

object OffsetResetStrategy extends Enum[OffsetResetStrategy] {
  override def values: immutable.IndexedSeq[OffsetResetStrategy] = findValues

  case object None       extends OffsetResetStrategy
  case object ToEarliest extends OffsetResetStrategy
  case object ToLatest   extends OffsetResetStrategy
}
