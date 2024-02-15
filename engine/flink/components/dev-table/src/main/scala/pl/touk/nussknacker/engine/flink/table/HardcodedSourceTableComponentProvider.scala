package pl.touk.nussknacker.engine.flink.table

import com.typesafe.config.Config
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.table.source.HardcodedValuesTableSourceFactory

class HardcodedSourceTableComponentProvider extends ComponentProvider {

  override def providerName: String = "tableApiHardcoded"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] =
    HardcodedSourceTableComponentProvider.ConfigIndependentComponents

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true

}

object HardcodedSourceTableComponentProvider {

  lazy val ConfigIndependentComponents: List[ComponentDefinition] =
    List(
      ComponentDefinition(
        "hardcodedSource-tableApi",
        HardcodedValuesTableSourceFactory
      )
    )

}
