package pl.touk.nussknacker.engine.flink.table.batch

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentDependencies,
  ComponentProvider,
  NussknackerVersion
}
import pl.touk.nussknacker.engine.flink.table.batch.FlinkTableOpsBatchComponentProvider.components
import pl.touk.nussknacker.engine.flink.table.batch.aggregate.TableAggregationFactory

class FlinkTableOpsBatchComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "flinkBatchOps"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, componentDependencies: ComponentDependencies): List[ComponentDefinition] =
    components

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = false

}

object FlinkTableOpsBatchComponentProvider {

  private[table] val components = {
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

}
