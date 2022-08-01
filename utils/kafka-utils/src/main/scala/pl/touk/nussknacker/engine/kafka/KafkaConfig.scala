package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.{OptionReader, ValueReader}

import scala.concurrent.duration._

case class SchemaRegistryClientKafkaConfig(
                                            kafkaProperties: Map[String, String],
                                            cacheConfig: SchemaRegistryCacheConfig,
                                            avroAsJsonSerialization: Option[Boolean]
                                          )

case class KafkaConfig(kafkaAddress: String,
                       kafkaProperties: Option[Map[String, String]],
                       kafkaEspProperties: Option[Map[String, String]],
                       consumerGroupNamingStrategy: Option[ConsumerGroupNamingStrategy.Value] = None,
                       // Probably better place for this flag would be configParameters inside global parameters but
                       // for easier usage in AbstractConfluentKafkaAvroDeserializer and ConfluentKafkaAvroDeserializerFactory it is placed here
                       //TODO rename to avroGenericRecordSchemaIdSerialization
                       avroKryoGenericRecordSchemaIdSerialization: Option[Boolean] = None,
                       topicsExistenceValidationConfig: TopicsExistenceValidationConfig = TopicsExistenceValidationConfig(enabled = false),
                       // By default we want to handle keys as ordinary String. For specific scenario,
                       // when complex key with its own schema is provided, this flag is false
                       // and all topics related to this config require both key and value schema definitions.
                       useStringForKey: Boolean = true,
                       schemaRegistryCacheConfig: SchemaRegistryCacheConfig = SchemaRegistryCacheConfig(),
                       avroAsJsonSerialization: Option[Boolean] = None
                      ) {

  def schemaRegistryClientKafkaConfig = SchemaRegistryClientKafkaConfig(kafkaProperties.getOrElse(Map.empty), schemaRegistryCacheConfig, avroAsJsonSerialization)

  def forceLatestRead: Option[Boolean] = kafkaEspProperties.flatMap(_.get("forceLatestRead")).map(_.toBoolean)

  def defaultMaxOutOfOrdernessMillis: Option[Long]
  = kafkaEspProperties.flatMap(_.get("defaultMaxOutOfOrdernessMillis")).map(_.toLong)
}

object ConsumerGroupNamingStrategy extends Enumeration {
  val ProcessId: ConsumerGroupNamingStrategy.Value = Value("processId")
  val ProcessIdNodeId: ConsumerGroupNamingStrategy.Value = Value("processId-nodeId")
}

object KafkaConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import TopicsExistenceValidationConfig._

  val defaultGlobalKafkaConfigPath = "kafka"

  def parseConfigOpt(config: Config, path: String = defaultGlobalKafkaConfigPath): Option[KafkaConfig] = {
    config.getAs[KafkaConfig](path)
  }

  def parseConfig(config: Config, path: String = defaultGlobalKafkaConfigPath): KafkaConfig = {
    config.as[KafkaConfig](path)
  }

}

case class TopicsExistenceValidationConfig(enabled: Boolean, validatorConfig: CachedTopicsExistenceValidatorConfig = CachedTopicsExistenceValidatorConfig.DefaultConfig)

object TopicsExistenceValidationConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  // scala 2.11 needs it
  implicit val valueReader: ValueReader[Option[TopicsExistenceValidationConfig]] = new ValueReader[Option[TopicsExistenceValidationConfig]] {
    override def read(config: Config, path: String): Option[TopicsExistenceValidationConfig] = OptionReader.optionValueReader[TopicsExistenceValidationConfig].read(config, path)
  }
}

case class CachedTopicsExistenceValidatorConfig(autoCreateFlagFetchCacheTtl: FiniteDuration,
                                                topicsFetchCacheTtl: FiniteDuration,
                                                adminClientTimeout: FiniteDuration)

object CachedTopicsExistenceValidatorConfig {
  val AutoCreateTopicPropertyName = "auto.create.topics.enable"
  val DefaultConfig: CachedTopicsExistenceValidatorConfig = CachedTopicsExistenceValidatorConfig(
    autoCreateFlagFetchCacheTtl = 5 minutes, topicsFetchCacheTtl = 30 seconds, adminClientTimeout = 500 millis
  )
}