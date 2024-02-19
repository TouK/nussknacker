package pl.touk.nussknacker.engine.flink.table.kafka

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader

class KafkaTableApiComponentProvider extends ComponentProvider {

  override def providerName: String = "kafkaTable"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val kafkaTableConfig = config.rootAs[KafkaTableApiConfig]

    List(
      ComponentDefinition(
        "KafkaSource-TableApi",
        new KafkaSourceFactory(kafkaTableConfig)
      ),
      ComponentDefinition(
        "KafkaSink-TableApi",
        new KafkaSinkFactory(kafkaTableConfig)
      )
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false

}

/**
 * TODO local: here should be:
 *  - schema
 *  - data format
 */
final case class KafkaTableApiConfig(kafkaProperties: Map[String, String])
