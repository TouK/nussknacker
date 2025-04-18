package pl.touk.nussknacker.engine.flink

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies

class FlinkScenarioCompilationDependencies(executionEnvironment: StreamExecutionEnvironment)
    extends EngineScenarioCompilationDependencies {

  override def nodeCompilationDependencies: List[NodeDependencyValue] = List(
    TypedNodeDependencyValue(executionEnvironment)
  )

}
