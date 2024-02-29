package pl.touk.nussknacker.engine.flink.table

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.table.extractor.DataSourceSqlExtractor.extractTablesFromFlinkRuntime
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.TableComponentProvider.ConfigIndependentComponents
import pl.touk.nussknacker.engine.flink.table.sink.TableSinkFactory
import pl.touk.nussknacker.engine.flink.table.source.{
  HardcodedSchemaTableSourceFactory,
  HardcodedValuesTableSourceFactory,
  SqlTableSourceFactory
}
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig
import net.ceedubs.ficus.Ficus._
import pl.touk.nussknacker.engine.flink.table.TableDataSourcesConfig.defaultDataSourceDefinitionFileName
import pl.touk.nussknacker.engine.flink.table.extractor.{SqlDataSourceConfig, SqlStatementReader}
import pl.touk.nussknacker.engine.util.ResourceLoader

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success, Try}

class TableComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "tableApi"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {

    val dataSourceComponentsFromConfig: List[ComponentDefinition] = for {
      tableDataSourcesConfig <- parseConfigOpt(config).toList
      dataSourceConfig       <- tableDataSourcesConfig.dataSources
      tableComponentDefinitions <- List(
        ComponentDefinition(
          tableDataSourceComponentId("source", dataSourceConfig.connector, dataSourceConfig.name),
          new HardcodedSchemaTableSourceFactory(dataSourceConfig)
        ),
        ComponentDefinition(
          tableDataSourceComponentId("sink", dataSourceConfig.connector, dataSourceConfig.name),
          new TableSinkFactory(dataSourceConfig)
        )
      )
    } yield tableComponentDefinitions

    val sqlSourceComponentsFromConfig: List[ComponentDefinition] = for {
      tableDataSourcesConfig <- parseConfigOpt(config).toList
      sqlStatementsFromFile  <- extractDataSourceConfigFromSqlFile(tableDataSourcesConfig.sqlFilePath)
      sqlComponentDefinitions <- List(
        ComponentDefinition(
          tableDataSourceComponentId("source-sql", sqlStatementsFromFile.connector, sqlStatementsFromFile.name),
          new SqlTableSourceFactory(sqlStatementsFromFile)
        )
      )
    } yield sqlComponentDefinitions

    ConfigIndependentComponents ::: dataSourceComponentsFromConfig ::: sqlSourceComponentsFromConfig
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

  private def extractDataSourceConfigFromSqlFile(filePath: String): List[SqlDataSourceConfig] = {
    val sqlStatements = readSqlFromFile(Paths.get(filePath))
    val results       = extractTablesFromFlinkRuntime(sqlStatements)

    // TODO: just log errors or crash? Can it be considered recoverable in all cases?
    results.flatMap {
      case Valid(config) => Some(config)
      case Invalid(error) =>
        logger.error(error.toString)
        None
    }
  }

  private def readSqlFromFile(pathToFile: Path): List[SqlStatement] =
    Try {
      val fileContent = ResourceLoader.load(pathToFile)
      SqlStatementReader.readSql(fileContent.mkString)
    } match {
      case Failure(exception) =>
        logger.warn(s"Couldn't parse sql tables definition: $exception")
        List.empty
      case Success(value) => value
    }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true

}

object TableComponentProvider {

  lazy val ConfigIndependentComponents: List[ComponentDefinition] =
    List(
      ComponentDefinition(
        "tableApi-source-hardcoded",
        HardcodedValuesTableSourceFactory
      )
    )

}

final case class TableDataSourcesConfig(
    dataSources: List[DataSourceConfig],
    sqlFilePath: String = defaultDataSourceDefinitionFileName
)

object TableDataSourcesConfig {
  private val defaultDataSourceDefinitionFileName = "../../../nussknacker-dist/src/universal/conf/tables-definition.sql"
}

final case class DataSourceConfig(
    name: String,
    options: Map[String, String] = Map.empty,
    connector: String,
    format: String
)
