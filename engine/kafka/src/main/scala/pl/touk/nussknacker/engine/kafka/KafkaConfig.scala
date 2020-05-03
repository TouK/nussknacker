package pl.touk.nussknacker.engine.kafka

import com.typesafe.config.Config

case class KafkaConfig(kafkaAddress: String,
                       kafkaProperties: Option[Map[String, String]],
                       kafkaEspProperties: Option[Map[String, String]])

object KafkaConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def parseConfig(config: Config, path: String): KafkaConfig = {
    config.as[KafkaConfig](path)
  }

}
