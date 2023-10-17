package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentType.ComponentType
import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentType}
import pl.touk.nussknacker.engine.definition.ComponentIdProvider
import pl.touk.nussknacker.restmodel.component.{ComponentIdParts, NodeId, NodeUsageData, ScenarioComponentsUsages}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails
import pl.touk.nussknacker.restmodel.component.NodeUsageData._

object ComponentsUsageHelper {

  import pl.touk.nussknacker.engine.util.Implicits._

  def computeComponentsUsageCount(
      componentIdProvider: ComponentIdProvider,
      processesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]]
  ): Map[ComponentId, Long] = {
    computeComponentsUsage(componentIdProvider, processesDetails)
      .mapValuesNow(usages => usages.map { case (_, nodeIds) => nodeIds.size }.sum)
  }

  def computeComponentsUsage(
      componentIdProvider: ComponentIdProvider,
      processesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]]
  ): Map[ComponentId, List[(BaseProcessDetails[_], List[NodeUsageData])]] = {
    def flattenUsages(processesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]]) = for {
      processDetails       <- processesDetails
      componentIdNodesPair <- processDetails.json.value.toList
      (ComponentIdParts(componentName, componentType), nodeIds) = componentIdNodesPair
      componentId = componentIdProvider.createComponentId(processDetails.processingType, componentName, componentType)
      nodeId <- nodeIds
    } yield ScenarioComponentsUsage[NodeId](componentId, componentType, componentName, processDetails, nodeId)

    val scenariosComponentUsages        = flattenUsages(processesDetails.filter(_.isFragment == false))
    val fragmentsComponentUsages        = flattenUsages(processesDetails.filter(_.isFragment == true))
    val groupedFragmentsComponentUsages = fragmentsComponentUsages.groupBy(_.processDetails.name)

    val scenarioUsagesWithResolvedFragments: List[ScenarioComponentsUsage[NodeUsageData]] =
      scenariosComponentUsages.flatMap {
        case fragmentUsage @ ScenarioComponentsUsage(
              _,
              ComponentType.Fragments,
              Some(fragmentName),
              processDetails,
              fragmentNodeId
            ) =>
          val fragmentUsageRefined: ScenarioComponentsUsage[NodeUsageData] =
            fragmentUsage.copy(nodeUsageData = ScenarioUsageData(fragmentNodeId))
          val fragmentsUsages: List[ScenarioComponentsUsage[NodeUsageData]] =
            groupedFragmentsComponentUsages.get(fragmentName).toList.flatten.map {
              case u @ ScenarioComponentsUsage(_, _, _, _, nodeId) =>
                val refinedUsage: ScenarioComponentsUsage[NodeUsageData] =
                  u.copy(processDetails = processDetails, nodeUsageData = FragmentUsageData(fragmentNodeId, nodeId))
                refinedUsage
            }
          fragmentUsageRefined :: fragmentsUsages
        case usageOfOtherComponentType @ ScenarioComponentsUsage(_, _, _, _, nodeId) =>
          val usageOfOtherComponentTypeRefined: ScenarioComponentsUsage[NodeUsageData] =
            usageOfOtherComponentType.copy(nodeUsageData = ScenarioUsageData(nodeId))
          List(usageOfOtherComponentTypeRefined)
      }

    val fragmentUsages: List[ScenarioComponentsUsage[NodeUsageData]] = fragmentsComponentUsages.flatMap {
      case componentUsage @ ScenarioComponentsUsage(_, _, _, _, nodeId: NodeId) =>
        val usage: ScenarioComponentsUsage[NodeUsageData] =
          componentUsage.copy(nodeUsageData = ScenarioUsageData(nodeId))
        List(usage)
    }

    (scenarioUsagesWithResolvedFragments ++ fragmentUsages)
      .groupBy(_.componentId)
      .mapValuesNow(
        _.groupBy(_.processDetails)
          .mapValuesNow { usages =>
            usages.map(_.nodeUsageData)
          }
          .toList
      )
  }

  private final case class ScenarioComponentsUsage[NodeUsageDataShape](
      componentId: ComponentId,
      componentType: ComponentType,
      componentName: Option[String],
      processDetails: BaseProcessDetails[_],
      nodeUsageData: NodeUsageDataShape
  )

}
