package pl.touk.nussknacker.engine.flink.table.autoloaded

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies

class HardcodedTableApiComponentProvider extends ComponentProvider {

  override def providerName: String = "tableApi"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    HardcodedTableApiComponentProvider.ConfigIndependentComponents
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true

}

object HardcodedTableApiComponentProvider {

  lazy val ConfigIndependentComponents: List[ComponentDefinition] =
    List(
      ComponentDefinition(
        "boundedSource-tableApi",
        BoundedSourceFactory
      )
    )

}
