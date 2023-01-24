package pl.touk.nussknacker.engine.schemedkafka.schemaregistry.azure

import com.azure.core.util.{Configuration, ConfigurationBuilder}

// This class enrich Azure properties with our properties from kafka configuration to be possible to tweak some
// parameters. Caveat: It wasn's tested intensively, from my observation most of properties can't be configured this
// way because most Azure classes uses Configuration.get(String) method which look only into Configuration.environmentConfiguration
// which are not changed by ConfigurationBuilder.putProperty
object AzureConfigurationFactory {
  def createFromKafkaProperties(kafkaProperties: Map[String, String]): Configuration = {
    val configBuilder = new ConfigurationBuilder()
    kafkaProperties.foreach {
      case (k, v) =>
        configBuilder.putProperty(k, v)
        // Most of Configuration.PROPERTY_* keys are SCREAMING_SNAKE_CASE
        configBuilder.putProperty(k.replace(".", "_").toUpperCase, v)
    }
    configBuilder.build()
  }

}
