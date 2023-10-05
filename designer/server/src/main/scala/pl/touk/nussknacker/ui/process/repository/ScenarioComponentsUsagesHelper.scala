package pl.touk.nussknacker.ui.process.repository

import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.component.ComponentUtil
import pl.touk.nussknacker.restmodel.component.{ComponentIdParts, ScenarioComponentsUsages}

object ScenarioComponentsUsagesHelper {

  def compute(scenario: CanonicalProcess): ScenarioComponentsUsages = {
    val usagesList = for {
      node          <- scenario.collectAllNodes
      componentType <- ComponentUtil.extractComponentType(node)
      componentName = ComponentUtil.extractComponentName(node)
    } yield {
      (componentName, componentType, node.id)
    }
    val usagesMap = usagesList
      // Can be replaced with .groupMap from Scala 2.13.
      .groupBy { case (componentName, componentType, _) => ComponentIdParts(componentName, componentType) }
      .transform { (_, usages) => usages.map { case (_, _, nodeId) => nodeId } }
    ScenarioComponentsUsages(usagesMap)
  }

}
