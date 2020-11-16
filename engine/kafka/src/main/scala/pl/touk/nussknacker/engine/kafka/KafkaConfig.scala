package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies

case class KafkaConfig(kafkaAddress: String,
                       kafkaProperties: Option[Map[String, String]],
                       kafkaEspProperties: Option[Map[String, String]],
                       consumerGroupNamingStrategy: Option[ConsumerGroupNamingStrategy.Value] = None) {

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

  val defaultGlobalKafkaConfigPath = "kafka"

  def parseConfigOpt(config: Config, path: String = defaultGlobalKafkaConfigPath): Option[KafkaConfig] = {
    config.getAs[KafkaConfig](path)
  }

  def parseConfig(config: Config, path: String = defaultGlobalKafkaConfigPath): KafkaConfig = {
    config.as[KafkaConfig](path)
  }

  def parseProcessObjectDependencies(processObjectDependencies: ProcessObjectDependencies): KafkaConfig =
    parseConfig(processObjectDependencies.config, defaultGlobalKafkaConfigPath)
}
