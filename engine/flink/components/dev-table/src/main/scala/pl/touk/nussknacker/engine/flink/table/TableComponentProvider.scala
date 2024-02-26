package pl.touk.nussknacker.engine.flink.table

import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.table.TableComponentProvider.{
  ConfigIndependentComponents,
  dataSourceConfigPath
}
import pl.touk.nussknacker.engine.flink.table.source.{ConfigurableTableSourceFactory, HardcodedValuesTableSourceFactory}

class TableComponentProvider extends ComponentProvider {

  override def providerName: String = "tableApi"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val tableDataSourcesConfigOpt = parseConfigOpt(config)
    val sources = tableDataSourcesConfigOpt.map { a =>
      ComponentDefinition(s"tableApi-source-${a.connector}", new ConfigurableTableSourceFactory(a))
    }.toList

    ConfigIndependentComponents ::: sources
  }

  private def parseConfigOpt(config: Config): Option[TableSourceConfig] =
    config.getAs[TableSourceConfig](dataSourceConfigPath)

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true

}

object TableComponentProvider {
  private val dataSourceConfigPath = "dataSource"

  lazy val ConfigIndependentComponents: List[ComponentDefinition] =
    List(
      ComponentDefinition(
        "tableApi-source-hardcoded",
        HardcodedValuesTableSourceFactory
      )
    )

}

final case class TableSourceConfig(options: Map[String, String] = Map.empty, connector: String, format: String)
