package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.node.ComponentIdExtractor
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList
import pl.touk.nussknacker.restmodel.component.ScenarioComponentsUsages

object ScenarioComponentsUsagesHelper {

  def compute(scenario: CanonicalProcess): ScenarioComponentsUsages = {
    val usagesList = for {
      node        <- scenario.collectAllNodes
      componentId <- ComponentIdExtractor.fromScenarioNode(node)
    } yield {
      (componentId, node.id)
    }
    val usagesMap = usagesList.toGroupedMap
    ScenarioComponentsUsages(usagesMap)
  }

}
