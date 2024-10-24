package pl.touk.nussknacker.http

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.http.enricher.HttpEnricherFactory

class HttpEnricherComponentProvider extends ComponentProvider with LazyLogging {
  override def providerName: String = "http"

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val conf = HttpEnricherConfig.parse(config)
    ComponentDefinition("http", new HttpEnricherFactory(conf)) :: Nil
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def resolveConfigForExecution(config: Config): Config = config

  override def isAutoLoaded: Boolean = false
}
