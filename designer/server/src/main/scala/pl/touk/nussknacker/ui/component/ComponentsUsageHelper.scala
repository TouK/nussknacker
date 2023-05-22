package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.restmodel.component.{ComponentIdParts, NodeId, ScenarioComponentsUsages}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails

object ComponentsUsageHelper {

  import pl.touk.nussknacker.engine.util.Implicits._

  def computeComponentsUsageCount(componentIdProvider: ComponentIdProvider,
                                  processesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]]): Map[ComponentId, Long] = {
    computeComponentsUsage(componentIdProvider, processesDetails)
      .mapValuesNow(usages => usages.map { case (_, nodeIds) => nodeIds.size }.sum)
  }

  def computeComponentsUsage(componentIdProvider: ComponentIdProvider,
                             processesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]]): Map[ComponentId, List[(BaseProcessDetails[Unit], List[NodeId])]] = {

    def toComponentIdUsages(processDetails: BaseProcessDetails[ScenarioComponentsUsages]): List[(ComponentId, (BaseProcessDetails[Unit], List[NodeId]))] = {
      val componentsUsages: Map[ComponentIdParts, List[NodeId]] = processDetails.json.value
      componentsUsages.toList.map { case (ComponentIdParts(componentName, componentType), nodeIds) =>
        val componentId = componentIdProvider.createComponentId(processDetails.processingType, componentName, componentType)
        componentId -> (processDetails.mapProcess(_ => ()), nodeIds)
      }
    }

    processesDetails
      .flatMap(toComponentIdUsages)
      // Can be replaced with .groupMap from Scala 2.13.
      .groupBy { case (componentId, _) => componentId }
      .transform { case (_, usages) => usages.map { case (_, processDetails) => processDetails } }
  }

}
