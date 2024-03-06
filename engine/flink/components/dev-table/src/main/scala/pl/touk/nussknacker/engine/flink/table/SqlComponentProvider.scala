package pl.touk.nussknacker.engine.flink.table

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.table.extractor.DataSourceSqlExtractor.extractTablesFromFlinkRuntime
import pl.touk.nussknacker.engine.flink.table.extractor.SqlStatementReader.SqlStatement
import pl.touk.nussknacker.engine.flink.table.extractor.{DataSourceTableDefinition, SqlStatementReader}
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

    val configs = extractDataSourceConfigFromSqlFileOrThrow(parsedConfig.sqlFilePath)

    ComponentDefinition(
      "tableApi-source-sql",
      new SqlSourceFactory(configs)
    ) :: Nil
  }

  private def extractDataSourceConfigFromSqlFileOrThrow(filePath: String): SqlDataSourcesDefinition = {
    val sqlStatements = readSqlFromFile(Paths.get(filePath))
    val results       = extractTablesFromFlinkRuntime(sqlStatements)

    if (results.sqlStatementExecutionErrors.nonEmpty) {
      throw new RuntimeException(
        "Errors occurred when parsing sql component configuration file: " + results.sqlStatementExecutionErrors
          .map(_.message)
          .mkString(", ")
      )
    }

    SqlDataSourcesDefinition(results.tableDefinitions, sqlStatements)
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

  override def isAutoLoaded: Boolean = false

}

final case class SqlDataSourcesDefinition(
    tableDefinitions: List[DataSourceTableDefinition],
    sqlStatements: List[SqlStatement]
)

final case class SqlFileDataSourcesConfig(sqlFilePath: String)
