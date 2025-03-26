package pl.touk.nussknacker.engine.compile

import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue

trait EngineNodeDependencies {
  def dependencies: List[NodeDependencyValue]
}

object EngineNodeDependencies {
  val empty: EngineNodeDependencies = new EngineNodeDependencies {
    override def dependencies: List[NodeDependencyValue] = List.empty
  }
}
