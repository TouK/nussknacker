package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.restmodel.component.{ComponentIdParts, NodeId, NodeMetadata, ScenarioComponentsUsages}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.restmodel.component.NodeMetadata._

object ComponentsUsageHelper {

  import pl.touk.nussknacker.engine.util.Implicits._

  def computeComponentsUsageCount(componentIdProvider: ComponentIdProvider,
                                  processesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]]): Map[ComponentId, Long] = {
    computeComponentsUsage(componentIdProvider, processesDetails)
      .mapValuesNow(usages => usages.map { case (_, nodeIds) => nodeIds.size }.sum)
  }

  def computeComponentsUsage(componentIdProvider: ComponentIdProvider,
                             processesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]]): Map[ComponentId, List[(BaseProcessDetails[_], List[NodeMetadata])]] = {
    def flattenUsages(processesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]]) = for {
      processDetails <- processesDetails
      componentIdNodesPair <- processDetails.json.value.toList
      (ComponentIdParts(componentName, componentType), nodeIds) = componentIdNodesPair
      componentId = componentIdProvider.createComponentId(processDetails.processingType, componentName, componentType)
      nodeId <- nodeIds
      nodeMetadata = if (processDetails.isSubprocess) FragmentNodeMetadata(processDetails.name, nodeId) else ScenarioNodeMetadata(nodeId)
    } yield ScenarioComponentsUsage(componentId, componentType, componentName, processDetails, nodeMetadata)

    val scenariosComponentUsagesFlatten = flattenUsages(processesDetails.filter(_.isSubprocess == false))
    val fragmentsComponentUsagesFlattenMap = flattenUsages(processesDetails.filter(_.isSubprocess == true))
      .groupBy(_.processDetails.name).mapValuesNow(_.collect {
      case u@ScenarioComponentsUsage(_, _, _, , _, FragmentNodeMetadata(fragmentNodeId, nodeId)) =>
        u.copy(nodeMetadata = FragmentNodeMetadata(fragmentNodeId, s"<<fragment>> ${nodeId}"))
    })

    val scenarioUsagesWithResolvedFragments = scenariosComponentUsagesFlatten.flatMap {
      case fragmentUsage@ScenarioComponentsUsage(_, ComponentType.Fragments, Some(fragmentName), processDetails, ScenarioNodeMetadata(fragmentNodeId)) =>
        fragmentUsage :: fragmentsComponentUsagesFlattenMap.get(fragmentName).toList.flatten.collect {
          case u@ScenarioComponentsUsage(_, _, _, _, FragmentNodeMetadata(_, nodeId)) =>
            u.copy(processDetails = processDetails, nodeMetadata = FragmentNodeMetadata(fragmentNodeId, nodeId))
        }
      case usageOfOtherComponentType =>
        List(usageOfOtherComponentType)
    }

    scenarioUsagesWithResolvedFragments
      .groupBy(_.componentId)
      .mapValuesNow(_.groupBy(_.processDetails).mapValuesNow { usages =>
        usages.map(_.nodeMetadata)
      }.toList)
  }
  private case class ScenarioComponentsUsage(componentId: ComponentId, componentType: ComponentType, componentName: Option[String], processDetails: BaseProcessDetails[_], nodeMetadata: NodeMetadata)

}
