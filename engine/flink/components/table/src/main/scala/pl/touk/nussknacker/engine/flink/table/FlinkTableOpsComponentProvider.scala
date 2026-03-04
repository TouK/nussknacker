package pl.touk.nussknacker.engine.flink.table

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentDependencies,
  ComponentProvider,
  NussknackerVersion
}
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregationFactory
import pl.touk.nussknacker.engine.flink.table.join.TableJoinComponent

class FlinkTableOpsComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "flinkBatchOps"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(
      componentProviderConfig: Config,
      componentDependencies: ComponentDependencies
  ): List[ComponentDefinition] = components

  private[table] lazy val components = {
    List(
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

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true

}
