package pl.touk.nussknacker.engine.flink.table

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.table.DataSourceFromSqlExtractor.extractTablesFromFlinkRuntime
import pl.touk.nussknacker.engine.flink.table.SqlFromResourceReader.readFileFromResources
import pl.touk.nussknacker.engine.flink.table.TableComponentProvider.{
  ConfigIndependentComponents,
  defaultDataSourceDefinitionFileName
}
import pl.touk.nussknacker.engine.flink.table.sink.TableSinkFactory
import pl.touk.nussknacker.engine.flink.table.source.{
  HardcodedSchemaTableSourceFactory,
  HardcodedValuesTableSourceFactory,
  SqlTableSourceFactory
}
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig

import scala.util.{Failure, Success, Try}

class TableComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "tableApi"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {

    val dataSourceComponentsFromConfig: List[ComponentDefinition] = for {
      tableDataSourcesConfig <- parseConfigOpt(config).toList
      dataSourceConfig       <- tableDataSourcesConfig.dataSources
      componentDefinitions <- List(
        ComponentDefinition(
          tableDataSourceComponentId("source", dataSourceConfig.connector, dataSourceConfig.name),
          new HardcodedSchemaTableSourceFactory(dataSourceConfig)
        ),
        ComponentDefinition(
          tableDataSourceComponentId("sink", dataSourceConfig.connector, dataSourceConfig.name),
          new TableSinkFactory(dataSourceConfig)
        )
      )
    } yield componentDefinitions

    val dataSourceConfigFromSql = extractDataSourceConfigFromSqlFile()
    val sqlDataSourceComponents = dataSourceConfigFromSql.map(sqlConfig =>
      ComponentDefinition(
        tableDataSourceComponentId("source", sqlConfig.connector, sqlConfig.name),
        new SqlTableSourceFactory(sqlConfig)
      )
    )

    ConfigIndependentComponents ::: dataSourceComponentsFromConfig ::: sqlDataSourceComponents
  }

  private def tableDataSourceComponentId(
      componentType: String,
      connector: String,
      componentNamePart: String
  ): String = {
    s"tableApi-$componentType-$connector-$componentNamePart"
  }

  private def parseConfigOpt(config: Config): Option[TableDataSourcesConfig] = {
    val tryParse = Try(config.rootAs[TableDataSourcesConfig]) match {
      case f @ Failure(exception) =>
        logger.warn(s"Error parsing table component config: $exception")
        f
      case s @ Success(_) => s
    }
    tryParse.toOption
  }

  private def extractDataSourceConfigFromSqlFile(): List[SqlDataSourceConfig] = {
    val sqlStatements = readFileFromResources(defaultDataSourceDefinitionFileName)
    extractTablesFromFlinkRuntime(sqlStatements)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true

}

object TableComponentProvider {

  private val defaultDataSourceDefinitionFileName = "../../../nussknacker-dist/src/universal/conf/tables-definition.sql"

  lazy val ConfigIndependentComponents: List[ComponentDefinition] =
    List(
      ComponentDefinition(
        "tableApi-source-hardcoded",
        HardcodedValuesTableSourceFactory
      )
    )

}

final case class TableDataSourcesConfig(dataSources: List[DataSourceConfig])

final case class DataSourceConfig(
    name: String,
    options: Map[String, String] = Map.empty,
    connector: String,
    format: String
)
