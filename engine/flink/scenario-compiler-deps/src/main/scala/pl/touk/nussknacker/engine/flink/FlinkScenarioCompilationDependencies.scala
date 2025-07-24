package pl.touk.nussknacker.engine.flink

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.context.transformation.TypedNodeDependencyValue
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies

class FlinkScenarioCompilationDependencies(executionEnvironment: StreamExecutionEnvironment)
    extends EngineScenarioCompilationDependencies {

  override def nodeCompilationDependencies: List[TypedNodeDependencyValue] = List(
    TypedNodeDependencyValue(executionEnvironment)
  )

}
