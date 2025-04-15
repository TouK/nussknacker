package pl.touk.nussknacker.engine.flink

import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, TypedNodeDependencyValue}
import pl.touk.nussknacker.engine.api.definition.EngineNodeCompilationDependencies

class FlinkNodeCompilationDependencies(executionEnvironment: StreamExecutionEnvironment)
    extends EngineNodeCompilationDependencies {

  override def nodeCompilationDependencies: List[NodeDependencyValue] = List(
    TypedNodeDependencyValue(executionEnvironment)
  )

}
