package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.restmodel.component.ScenarioComponentsUsages

sealed trait ScenarioShapeFetchStrategy[ScenarioShape]

object ScenarioShapeFetchStrategy {
  implicit case object FetchScenarioGraph extends ScenarioShapeFetchStrategy[ScenarioGraph]

  implicit case object FetchCanonical extends ScenarioShapeFetchStrategy[CanonicalProcess]

  // In fact Unit won't be returned inside shape and Nothing would be more verbose but it won't help in compilation because Nothing <: DisplayableProcess
  implicit case object NotFetch extends ScenarioShapeFetchStrategy[Unit]

  implicit case object FetchComponentsUsages extends ScenarioShapeFetchStrategy[ScenarioComponentsUsages]
}
