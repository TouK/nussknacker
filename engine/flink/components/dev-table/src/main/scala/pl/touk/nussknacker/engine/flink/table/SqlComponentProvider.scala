package pl.touk.nussknacker.engine.flink.table

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.table.extractor.DataSourceSqlExtractor.extractTablesFromFlinkRuntime
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.sink.SqlSinkFactory
import pl.touk.nussknacker.engine.flink.table.source.SqlSourceFactory
import pl.touk.nussknacker.engine.util.ResourceLoader
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig

import java.nio.file.{Path, Paths}
import scala.util.{Failure, Success, Try}

/**
 * The config for this component provider is used in 2 contexts:
 *  - designer: to have typing based on schema
 *  - flink executor: to configure tables in a flink job
 *
 *  The file path of the sql file which is the source of component configuration is context dependent - for example if
 *  deploying inside docker container it has to point to a path inside the container. This is analogical to how the
 *  kafka and schema registry addresses are provided as environment variables that are different for designer and
 *  jobmanager/taskmanager services. For reference see the nussknacker-quickstart repository.
 */
class SqlComponentProvider extends ComponentProvider with LazyLogging {

  import net.ceedubs.ficus.Ficus._

  override def providerName: String = "sqlFile"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val parsedConfig = config.rootAs[SqlFileDataSourcesConfig]

    val definition = extractDataSourcesDefinitionFromSqlFileOrThrow(parsedConfig.sqlFilePath)

    ComponentDefinition(
      "tableApi-source-sql",
      new SqlSourceFactory(definition)
    ) :: ComponentDefinition(
      "tableApi-sink-sql",
      new SqlSinkFactory(definition)
    ) :: Nil
  }

  private def extractDataSourcesDefinitionFromSqlFileOrThrow(filePath: String) = {
    val sqlStatements = readSqlFromFile(Paths.get(filePath))
    val results       = extractTablesFromFlinkRuntime(sqlStatements)

    if (results.sqlStatementExecutionErrors.nonEmpty) {
      throw new IllegalStateException(
        "Errors occurred when parsing sql component configuration file: " + results.sqlStatementExecutionErrors
          .map(_.message)
          .mkString(", ")
      )
    }

    SqlDataSourcesDefinition(results.tableDefinitions, sqlStatements)
  }

  private def readSqlFromFile(pathToFile: Path) = Try(ResourceLoader.load(pathToFile)) match {
    case Failure(exception) =>
      throw new IllegalStateException(
        s"""Sql file with configuration of sql data source components was not found under specified path: $pathToFile.
             |Exception: $exception""".stripMargin
      )
    case Success(fileContent) =>
      SqlStatementReader.readSql(fileContent.mkString)
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false

}

final case class SqlDataSourcesDefinition(
    tableDefinitions: List[TableDefinition],
    sqlStatements: List[SqlStatement]
)

final case class SqlFileDataSourcesConfig(sqlFilePath: String)
