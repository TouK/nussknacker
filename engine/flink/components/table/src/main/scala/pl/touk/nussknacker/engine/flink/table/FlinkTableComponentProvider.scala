package pl.touk.nussknacker.engine.flink.table

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.table.FlinkTableComponentProvider.configIndependentComponents
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationFactory
import pl.touk.nussknacker.engine.flink.table.extractor.{DataDefinitionRegistrar, SqlStatementReader}
import pl.touk.nussknacker.engine.flink.table.join.TableJoinComponent
import pl.touk.nussknacker.engine.flink.table.sink.TableSinkFactory
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory
import pl.touk.nussknacker.engine.util.ResourceLoader

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
class FlinkTableComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "flinkTable"
  private val tableComponentName    = "table"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val parsedConfig                    = TableComponentProviderConfig.parse(config)
    val testDataGenerationModeOrDefault = parsedConfig.testDataGenerationMode.getOrElse(TestDataGenerationMode.default)
    val sqlStatements                   = parsedConfig.tableDefinitionFilePath.map(Paths.get(_)).map(readSqlFromFile)
    val dataDefinitionRegistrar         = DataDefinitionRegistrar(sqlStatements)

    ComponentDefinition(
      tableComponentName,
      new TableSourceFactory(
        dataDefinitionRegistrar,
        testDataGenerationModeOrDefault
      )
    ) :: ComponentDefinition(
      tableComponentName,
      new TableSinkFactory(dataDefinitionRegistrar)
    ) :: configIndependentComponents
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

object FlinkTableComponentProvider {

  val configIndependentComponents: List[ComponentDefinition] = List(
    ComponentDefinition(
      "aggregate",
      new TableAggregationFactory()
    ),
    ComponentDefinition(
      "join",
      TableJoinComponent
    )
  )

}

final case class TableComponentProviderConfig(
    tableDefinitionFilePath: Option[String],
    catalogConfiguration: Option[Map[String, String]],
    testDataGenerationMode: Option[TestDataGenerationMode]
)

object TableComponentProviderConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig

  private[table] def parse(config: Config) = config.rootAs[TableComponentProviderConfig]

  object TestDataGenerationMode extends Enumeration {
    type TestDataGenerationMode = Value
    val Random  = Value("random")
    val Live    = Value("live")
    val default = Live
  }

}
