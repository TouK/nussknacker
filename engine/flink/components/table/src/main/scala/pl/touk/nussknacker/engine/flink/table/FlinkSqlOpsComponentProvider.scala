package pl.touk.nussknacker.engine.flink.table

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentDependencies,
  ComponentProvider,
  NussknackerVersion
}
import pl.touk.nussknacker.engine.flink.table.sql.FlinkSqlQueryComponentFactory

class FlinkSqlOpsComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "flinkSqlOps"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(
      componentProviderConfig: Config,
      componentDependencies: ComponentDependencies
  ): List[ComponentDefinition] = components

  private[table] lazy val components = {
    List(
      ComponentDefinition(
        "flink-sql-query",
        FlinkSqlQueryComponentFactory
      )
    )
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true

}
