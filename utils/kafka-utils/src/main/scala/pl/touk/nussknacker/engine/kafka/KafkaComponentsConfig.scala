package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.Config
import enumeratum.{Enum, EnumEntry}
import pl.touk.nussknacker.engine.kafka.IdlenessConfig.DefaultDuration
import pl.touk.nussknacker.engine.kafka.KafkaComponentsConfig._

import scala.collection.immutable
import scala.compat.java8.DurationConverters.FiniteDurationops
import scala.concurrent.duration._

case class SchemaRegistryClientKafkaConfig(
    kafkaProperties: Map[String, String],
    cacheConfig: SchemaRegistryCacheConfig,
    avroAsJsonSerialization: Option[Boolean]
)

case class KafkaComponentsConfig(
    kafkaProperties: Map[String, String],
    // TODO: Replace this map with normal fields
    kafkaEspProperties: Option[Map[String, String]] = None,
    consumerGroupNamingStrategy: Option[ConsumerGroupNamingStrategy.Value] = None,
    topicsExistenceValidationConfig: TopicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = true),
    // By default we want to handle keys as ordinary String. For specific scenario,
    // when complex key with its own schema is provided, this flag is false
    // and all topics related to this config require both key and value schema definitions.
    useStringForKey: Boolean = true,
    schemaRegistryCacheConfig: SchemaRegistryCacheConfig = SchemaRegistryCacheConfig(),
    avroAsJsonSerialization: Option[Boolean] = None,
    idleTimeout: Option[IdlenessConfig] = None,
    sinkDeliveryGuarantee: Option[SinkDeliveryGuarantee.Value] = None,
    showTopicsWithoutSchema: Boolean = true,
    allowNotSuggestedTopicUsage: Boolean = false,
    kafkaAdminConfig: KafkaAdminConfig = KafkaAdminConfig(),
    useDataSampleParamForSchemalessJsonTopicBasedKafkaSource: Boolean = false,
) {

  val kafkaBootstrapServers: String = kafkaProperties
    .getOrElse(
      "bootstrap.servers",
      throw new IllegalArgumentException("No bootstrap.servers configured in kafkaProperties")
    )

  def schemaRegistryClientKafkaConfig: SchemaRegistryClientKafkaConfig = SchemaRegistryClientKafkaConfig(
    kafkaProperties,
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
      Some(finalIdleConfig.duration.toJava)
    } else {
      None
    }
  }

}

object ConsumerGroupNamingStrategy extends Enumeration {
  // TODO: Rename to processName and processName-nodeName
  val ProcessId: ConsumerGroupNamingStrategy.Value       = Value("processId")
  val ProcessIdNodeId: ConsumerGroupNamingStrategy.Value = Value("processId-nodeId")
}

object KafkaComponentsConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  val DefaultOffsetResetStrategyPath                            = "defaultOffsetResetStrategy"
  val DefaultMaxOutOfOrdernessMillisPath                        = "defaultMaxOutOfOrdernessMillis"
  val DefaultMaxOutOfOrdernessMillisDefault: java.time.Duration = java.time.Duration.ofMillis(60000)

  def parseConfig(config: Config): KafkaComponentsConfig = {
    config.as[KafkaComponentsConfig]
  }

  // Flink engine has kafka components config nested in componentConfig.config
  def parseConfigNestedAtConfigKey(config: Config): KafkaComponentsConfig = {
    config.as[KafkaComponentsConfig]("config")
  }

  // Lite engine has kafka components config nested in modelConfig.kafka
  def parseConfigNestedAtKafkaKey(config: Config): KafkaComponentsConfig = {
    config.as[KafkaComponentsConfig]("kafka")
  }

}

final case class TopicsExistenceValidationConfig(
    enabled: Boolean,
)

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

final case class KafkaAdminConfig(
    clientTimeout: FiniteDuration = 10 seconds,
    cacheConfig: KafkaAdminCacheConfig = KafkaAdminCacheConfig(),
)

final case class KafkaAdminCacheConfig(
    topicsExpirationTime: FiniteDuration = 30 seconds,
    autoCreateTopicSettingExpirationTime: FiniteDuration = 5 minutes,
)
