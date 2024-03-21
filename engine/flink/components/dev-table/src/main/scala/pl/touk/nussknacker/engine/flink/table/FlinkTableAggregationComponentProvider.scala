package pl.touk.nussknacker.engine.flink.table

import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{ComponentDefinition, ComponentProvider, NussknackerVersion}
import pl.touk.nussknacker.engine.api.process.ProcessObjectDependencies
import pl.touk.nussknacker.engine.flink.table.aggregate.TableAggregation

object FlinkTableAggregationComponentProvider extends ComponentProvider with LazyLogging {

  override def providerName: String = "tableAggregate"

  override def resolveConfigForExecution(config: Config): Config = config

  override def create(config: Config, dependencies: ProcessObjectDependencies): List[ComponentDefinition] = {
    components
  }

  override def isCompatible(version: NussknackerVersion): Boolean = true

  override def isAutoLoaded: Boolean = true

  val components: List[ComponentDefinition] = ComponentDefinition(
    "aggregate",
    new TableAggregation()
  ) :: Nil

}
