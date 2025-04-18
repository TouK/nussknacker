package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue

trait EngineScenarioCompilationDependencies {
  def nodeCompilationDependencies: List[NodeDependencyValue]
}

object EngineScenarioCompilationDependencies {

  val empty: EngineScenarioCompilationDependencies = new EngineScenarioCompilationDependencies {
    override val nodeCompilationDependencies: List[NodeDependencyValue] = List.empty
  }

}
