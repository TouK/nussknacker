package pl.touk.nussknacker.engine.flink.table

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import org.apache.flink.util.FlinkUserCodeClassLoaders.SafetyNetWrapperClassLoader
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.minicluster.FlinkMiniClusterFactory
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.TableComponentProviderConfig.TestDataGenerationMode.TestDataGenerationMode
import pl.touk.nussknacker.engine.flink.table.definition.{FlinkDataDefinition, FlinkDdlParser}
import pl.touk.nussknacker.engine.flink.table.sink.TableSinkFactory
import pl.touk.nussknacker.engine.flink.table.source.TableSourceFactory

import java.net.URLClassLoader
import scala.jdk.CollectionConverters._

class FlinkTableDataSourceComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "flinkTableDataSource"
  private val tableComponentName    = "table"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    val parsedConfig                    = TableComponentProviderConfig.parse(config)
    val testDataGenerationModeOrDefault = parsedConfig.testDataGenerationMode.getOrElse(TestDataGenerationMode.default)
    val sqlStatements                   = parsedConfig.tableDefinition
    val catalogConfigurationOpt         = parsedConfig.catalogConfiguration.map(_.asJava).map(Configuration.fromMap)
    val parsedSqlStatements             = sqlStatements.map(FlinkDdlParser.parseUnsafe).toList.flatten
    val flinkDataDefinition             = FlinkDataDefinition.applyUnsafe(parsedSqlStatements, catalogConfigurationOpt)

    List(
      ComponentDefinition(
        tableComponentName,
        new TableSourceFactory(
          flinkDataDefinition,
          testDataGenerationModeOrDefault
        )
      ),
      ComponentDefinition(
        tableComponentName,
        new TableSinkFactory(flinkDataDefinition)
      )
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false

  // TODO: Pass ModelClassLoader through API
  private def castModelClassloader() = {
    val modelClassLoader = Thread.currentThread().getContextClassLoader match {
      // When executing tests in Designer, a SafetyNetWrapperClassLoader is used with the ModelClassloader as parent
      case wrapperClassLoader: SafetyNetWrapperClassLoader =>
        wrapperClassLoader.getParent match {
          case cl: URLClassLoader => cl
          case _ =>
            throw new IllegalStateException(
              "FlinkTableDataSourceComponentProvider should be loaded with ModelClassLoader as context ClassLoader"
            )
        }
      case cl: URLClassLoader => cl
      case _ =>
        throw new IllegalStateException(
          "FlinkTableDataSourceComponentProvider should be loaded with ModelClassLoader as context ClassLoader"
        )
    }
    modelClassLoader
  }

}

final case class TableComponentProviderConfig(
    tableDefinition: Option[String],
    catalogConfiguration: Option[Map[String, String]],
    testDataGenerationMode: Option[TestDataGenerationMode]
)

object TableComponentProviderConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig

  private[table] def parse(config: Config) = config.rootAs[TableComponentProviderConfig]

  object TestDataGenerationMode extends Enumeration {
    type TestDataGenerationMode = Value
    val Random  = Value("random")
    val Live    = Value("live")
    val default = Live
  }

}
