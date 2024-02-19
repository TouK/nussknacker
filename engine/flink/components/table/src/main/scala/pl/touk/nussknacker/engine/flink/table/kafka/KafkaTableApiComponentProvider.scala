package pl.touk.nussknacker.engine.flink.table.kafka

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies

class KafkaTableApiComponentProvider extends ComponentProvider {

  override def providerName: String = "kafkaTable"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val eh = 1
    Nil
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  // TODO local: just for local development
  override def isAutoLoaded: Boolean = false

}
