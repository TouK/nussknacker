package pl.touk.nussknacker.ui.component

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.restmodel.component.{ComponentIdParts, ComponentMetadata, NodeId, ScenarioComponentsUsages}
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
    def toComponentIdUsages(processDetails: BaseProcessDetails[ScenarioComponentsUsages]): List[(ComponentId, ComponentMetadata)] = {
      val componentUsages = processDetails.json.value

      componentUsages.toList.map {
        case (ComponentIdParts(componentName, componentType), nodeIds) =>
          val componentId = componentIdProvider.createComponentId(processDetails.processingType, componentName, componentType)
          componentId -> ComponentMetadata(processDetails, nodeIds)
      }
    }

    def mergeFragmentIntoScenarios(componentId: ComponentId,
                                   fragmentComponentsMetadata: List[ComponentMetadata],
                                   acc: List[(ComponentId, List[ComponentMetadata])]): List[(ComponentId, List[ComponentMetadata])] =
      acc.map {
        case (scenarioComponentId, componentMetadataList) if scenarioComponentId.value == componentId.value =>
          val updatedComponentsMetadata = fragmentComponentsMetadata.foldLeft(componentMetadataList) {
            case (currentComponentMetadata, currentFragment) =>
              currentComponentMetadata.map(componentMetadata => {
                val scenarioNodeIds = toComponentIdUsages(componentMetadata.baseProcessDetails).map(_._2).flatMap(_.nodeIds)
                if (scenarioNodeIds.contains(currentFragment.baseProcessDetails.name))
                  componentMetadata.copy(nodeIds = componentMetadata.nodeIds ++ currentFragment.nodeIds.map(n => s"<<fragment>> $n"))
                else
                  componentMetadata
              })
          }

          (componentId, updatedComponentsMetadata)
        case (componentId, componentMetadataList) => (componentId, componentMetadataList)
      }

    val scenariosProcessesDetails = processesDetails.filter(_.isSubprocess == false)
    val fragmentsProcessesDetails = processesDetails.filter(_.isSubprocess == true)

    val scenariosComponentsUsages = scenariosProcessesDetails
      .flatMap(toComponentIdUsages)
      .groupBy { case (componentId, _) => componentId }
      .transform { case (_, usages) => usages.map { case (_, processDetails) => processDetails } }

    val fragmentsComponentsUsages = fragmentsProcessesDetails
      .flatMap(toComponentIdUsages)
      .groupBy { case (componentId, _) => componentId }
      .transform { case (_, usages) => usages.map { case (_, processDetails) => processDetails } }

    val merged =
      fragmentsComponentsUsages.toList.foldLeft(scenariosComponentsUsages.toList) {
        case (acc, (fragmentComponentId, fragmentComponentsMetadata)) => mergeFragmentIntoScenarios(fragmentComponentId, fragmentComponentsMetadata, acc)
      }

    val mergedWithUnitProcessesDetails =
      merged.map {
        case (componentId, componentsMetadata) => (componentId, componentsMetadata.map {
          case ComponentMetadata(baseProcessDetails, nodeIds) => (baseProcessDetails.mapProcess(_ => ()), nodeIds)
        })
      }.toMap

    mergedWithUnitProcessesDetails
  }
}
