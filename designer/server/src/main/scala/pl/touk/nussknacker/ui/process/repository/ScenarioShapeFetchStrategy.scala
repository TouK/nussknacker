package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.component.ScenarioComponentsUsages
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter

sealed trait ScenarioShapeFetchStrategy[ScenarioShape]

object ScenarioShapeFetchStrategy {
  implicit case object FetchScenarioGraph extends ScenarioShapeFetchStrategy[ScenarioGraph]

  implicit case object FetchCanonical extends ScenarioShapeFetchStrategy[CanonicalProcess]

  // In fact Unit won't be returned inside shape and Nothing would be more verbose but it won't help in compilation because Nothing <: DisplayableProcess
  implicit case object NotFetch extends ScenarioShapeFetchStrategy[Unit]

  implicit case object FetchComponentsUsages extends ScenarioShapeFetchStrategy[ScenarioComponentsUsages]

  def convertToTargetShape[TARGET: ScenarioShapeFetchStrategy](source: Any, processName: ProcessName): TARGET = {
    (source, implicitly[ScenarioShapeFetchStrategy[TARGET]]) match {
      case (c: CanonicalProcess, FetchScenarioGraph) => CanonicalProcessConverter.toScenarioGraph(c)
      case (c: CanonicalProcess, FetchCanonical)     => c
      case (s: ScenarioGraph, FetchScenarioGraph)    => s
      case (s: ScenarioGraph, FetchCanonical) =>
        CanonicalProcessConverter.fromScenarioGraph(s, processName)
      case (_, NotFetch)                                        => ()
      case (c: ScenarioComponentsUsages, FetchComponentsUsages) => c
      case (_, strategy) =>
        throw new IllegalArgumentException(
          s"Missing scenario json data, it's required to convert for strategy: $strategy."
        )
    }
  }

}
