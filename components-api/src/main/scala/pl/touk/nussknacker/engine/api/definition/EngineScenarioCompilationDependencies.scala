package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.context.transformation.{NodeDependencyValue, TypedNodeDependencyValue}

trait EngineScenarioCompilationDependencies {
  def nodeCompilationDependencies: List[TypedNodeDependencyValue]
}

object EngineScenarioCompilationDependencies {

  val empty: EngineScenarioCompilationDependencies = new EngineScenarioCompilationDependencies {
    override val nodeCompilationDependencies: List[TypedNodeDependencyValue] = List.empty
  }

}
