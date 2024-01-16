package pl.touk.nussknacker.engine.management.sample.transformer

import pl.touk.nussknacker.engine.api.context.transformation.NodeDependencyValue
import pl.touk.nussknacker.engine.api.process.SinkFactory
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink

object DynamicParametersSink extends SinkFactory with DynamicParametersMixin {

  override def runLogic(
      params: Map[String, Any],
      dependencies: List[NodeDependencyValue],
      finalState: Option[State]
  ): AnyRef = EmptySink

}
