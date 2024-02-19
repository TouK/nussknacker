package pl.touk.nussknacker.engine.flink.util.transformer

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies

class TableApiComponentProvider extends ComponentProvider {

  override def providerName: String = "tableApi"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    TableApiComponentProvider.ConfigIndependentComponents
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  // TODO local: just for local development
  override def isAutoLoaded: Boolean = true

}

object TableApiComponentProvider {

  lazy val ConfigIndependentComponents: List[ComponentDefinition] =
    List(
      ComponentDefinition(
        "BoundedSource-TableApi",
        BoundedSourceFactory
      )
    )

}
