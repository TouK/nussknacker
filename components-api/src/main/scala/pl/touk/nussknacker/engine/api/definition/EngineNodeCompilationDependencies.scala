package pl.touk.nussknacker.engine.api.definition

import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue

trait EngineNodeCompilationDependencies {
  def nodeCompilationDependencies: List[NodeDependencyValue]
}

object EngineNodeCompilationDependencies {

  val empty: EngineNodeCompilationDependencies = new EngineNodeCompilationDependencies {
    override val nodeCompilationDependencies: List[NodeDependencyValue] = List.empty
  }

}
