package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.restmodel.component.{ComponentIdParts, NodeId, NodeUsageData, ScenarioComponentsUsages}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.restmodel.component.NodeUsageData._

object ComponentsUsageHelper {

  import pl.touk.nussknacker.engine.util.Implicits._

  def computeComponentsUsageCount(componentIdProvider: ComponentIdProvider,
                                  processesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]]): Map[ComponentId, Long] = {
    computeComponentsUsage(componentIdProvider, processesDetails)
      .mapValuesNow(usages => usages.map { case (_, nodeIds) => nodeIds.size }.sum)
  }

  def computeComponentsUsage(componentIdProvider: ComponentIdProvider,
                             processesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]]): Map[ComponentId, List[(BaseProcessDetails[_], List[NodeUsageData])]] = {
    def flattenUsages(processesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]]) = for {
      processDetails <- processesDetails
      componentIdNodesPair <- processDetails.json.value.toList
      (ComponentIdParts(componentName, componentType), nodeIds) = componentIdNodesPair
      componentId = componentIdProvider.createComponentId(processDetails.processingType, componentName, componentType)
      nodeId <- nodeIds
    } yield ScenarioComponentsUsage[NodeId](componentId, componentType, componentName, processDetails, nodeId)

    val scenariosComponentUsagesFlatten = flattenUsages(processesDetails.filter(_.isSubprocess == false))
    val fragmentsComponentUsagesFlattenMap = flattenUsages(processesDetails.filter(_.isSubprocess == true))
      .groupBy(_.processDetails.name)

    val scenarioUsagesWithResolvedFragments: List[ScenarioComponentsUsage[NodeUsageData]] = scenariosComponentUsagesFlatten.flatMap {
      case fragmentUsage@ScenarioComponentsUsage(_, ComponentType.Fragments, Some(fragmentName), processDetails, fragmentNodeId) =>
        val fragmentUsageRefined: ScenarioComponentsUsage[NodeUsageData] = fragmentUsage.copy(nodeMetadata = ScenarioUsageData(fragmentNodeId))
        val fragmentsUsages: List[ScenarioComponentsUsage[NodeUsageData]] = fragmentsComponentUsagesFlattenMap.get(fragmentName).toList.flatten.map {
          case u@ScenarioComponentsUsage(componentId, componentType, componentName, _, nodeId) =>
            u.copy(processDetails = processDetails, nodeMetadata = FragmentUsageData(fragmentNodeId, nodeId))
        }
        fragmentUsageRefined :: fragmentsUsages
      case usageOfOtherComponentType@ScenarioComponentsUsage(_, _, _, _, nodeMetadata) =>
        val usageOfOtherComponentTypeRefined: ScenarioComponentsUsage[NodeUsageData] = usageOfOtherComponentType.copy(nodeMetadata = ScenarioUsageData(nodeMetadata))
        List(usageOfOtherComponentTypeRefined)
    }

    scenarioUsagesWithResolvedFragments
      .groupBy(_.componentId)
      .mapValuesNow(_.groupBy(_.processDetails).mapValuesNow { usages =>
        usages.map(_.nodeMetadata)
      }.toList)
  }
  private case class ScenarioComponentsUsage[NodeMetadataShape](componentId: ComponentId, componentType: ComponentType, componentName: Option[String], processDetails: BaseProcessDetails[_], nodeMetadata: NodeMetadataShape)

}
