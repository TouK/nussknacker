package pl.touk.nussknacker.engine.flink.table

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import net.ceedubs.ficus.Ficus._

class SourceTableComponentProvider extends ComponentProvider {

  override def providerName: String = "tableApiSource"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val tableDataSourcesConfigOpt = config.rootAs[TableSourceConfig]
    ComponentDefinition(
      s"configuredSource-${tableDataSourcesConfigOpt.connector}-tableApi",
      new TableSourceFactory(tableDataSourcesConfigOpt)
    ) :: Nil
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false

}

final case class TableSourceConfig(options: Map[String, String] = Map.empty, connector: String, format: String)
