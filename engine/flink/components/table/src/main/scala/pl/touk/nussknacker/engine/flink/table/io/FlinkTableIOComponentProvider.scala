package pl.touk.nussknacker.engine.flink.table.io

import com.typesafe.config.Config
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentDependencies,
  ComponentProvider,
  NussknackerVersion
}
import pl.touk.nussknacker.engine.flink.table.io.FlinkTableIOComponentProvider.{defaultCacheTtl, COMPONENT_NAME}
import pl.touk.nussknacker.engine.flink.table.io.definition.{FlinkDataDefinition, FlinkDdlParser}
import pl.touk.nussknacker.engine.flink.table.io.definition.discovery.{CachingTableDiscovery, TableDiscoveryImpl}
import pl.touk.nussknacker.engine.flink.table.io.definition.validation.{
  CachingTableUsageValidator,
  TableUsageValidatorImpl
}
import pl.touk.nussknacker.engine.flink.table.io.sink.TableSinkFactory
import pl.touk.nussknacker.engine.flink.table.io.source.{TableSourceFactory, TestDataGenerationMode}
import pl.touk.nussknacker.engine.flink.table.io.source.TestDataGenerationMode.TestDataGenerationMode

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._

class FlinkTableIOComponentProvider extends ComponentProvider {

  override def providerName: String = "flinkTableIO"

  override def create(config: Config, componentDependencies: ComponentDependencies): List[ComponentDefinition] =
    FlinkTableIOComponentProvider.create(config)

  override def resolveConfigForExecution(config: Config): Config  = config
  override def isCompatible(version: NussknackerVersion): Boolean = true
  override def isAutoLoaded: Boolean                              = false

}

object FlinkTableIOComponentProvider {
  import scala.concurrent.duration.DurationInt
  private val defaultCacheTtl: FiniteDuration = 1 minute
  val COMPONENT_NAME                          = "table"

  private[nussknacker] def create(componentProviderConfig: Config): List[ComponentDefinition] = {
    ensureJdbcDriversInitialized()
    val parsedConfig   = FlinkTableIOComponentProviderConfig.parse(componentProviderConfig)
    val ddl            = parsedConfig.tableDefinition.map(FlinkDdlParser.parseUnsafe).toList.flatten
    val catalogCfg     = parsedConfig.catalogConfiguration.map(_.asJava).map(Configuration.fromMap)
    val dataDefinition = FlinkDataDefinition.applyUnsafe(ddl, catalogCfg)

    val discoveryCacheTtl = parsedConfig.tablesDiscoveryCacheTtl.getOrElse(defaultCacheTtl)
    val tableDiscovery =
      new CachingTableDiscovery(discoveryCacheTtl, new TableDiscoveryImpl(databasesOmitList = List.empty))

    val validationCacheTtl = parsedConfig.tablesValidationCacheTtl.getOrElse(defaultCacheTtl)
    val modelClassLoader   = getClass.getClassLoader // TODO: pass ModelClassloader here and use it
    val tableValidator =
      new CachingTableUsageValidator(validationCacheTtl, new TableUsageValidatorImpl(modelClassLoader))

    val testingMode = parsedConfig.testDataGenerationMode.getOrElse(TestDataGenerationMode.default)

    List(
      ComponentDefinition(
        COMPONENT_NAME,
        new TableSourceFactory(dataDefinition, testingMode, tableDiscovery, tableValidator)
      ),
      ComponentDefinition(
        COMPONENT_NAME,
        new TableSinkFactory(dataDefinition, tableDiscovery, tableValidator)
      )
    )
  }

  // MySQL JDBC driver has to be manually initialized
  private def ensureJdbcDriversInitialized(): Unit = {
    try {
      Class.forName("com.mysql.cj.jdbc.Driver").getName
    } catch {
      case _: ClassNotFoundException =>
    }
  }

}

final case class FlinkTableIOComponentProviderConfig(
    tableDefinition: Option[String],
    catalogConfiguration: Option[Map[String, String]],
    testDataGenerationMode: Option[TestDataGenerationMode],
    tablesDiscoveryCacheTtl: Option[FiniteDuration],
    tablesValidationCacheTtl: Option[FiniteDuration]
)

object FlinkTableIOComponentProviderConfig {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments.RichConfig

  private[table] def parse(config: Config) = config.rootAs[FlinkTableIOComponentProviderConfig]

}
