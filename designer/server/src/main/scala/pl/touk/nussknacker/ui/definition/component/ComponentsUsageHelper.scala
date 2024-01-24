package pl.touk.nussknacker.ui.definition.component

import pl.touk.nussknacker.engine.api.component.{ComponentId, ComponentInfo, ComponentType}
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessingType}
import pl.touk.nussknacker.restmodel.component.NodeUsageData._
import pl.touk.nussknacker.restmodel.component.{NodeId, NodeUsageData, ScenarioComponentsUsages}
import pl.touk.nussknacker.ui.process.repository.ScenarioWithDetailsEntity

object ComponentsUsageHelper {

  import pl.touk.nussknacker.engine.util.Implicits._

  def computeComponentsUsageCount(
      processesDetails: List[ScenarioWithDetailsEntity[ScenarioComponentsUsages]],
      processingTypeAndInfoToNonFragmentComponentId: Map[(ProcessingType, ComponentInfo), ComponentId]
  ): Map[ComponentId, Long] = {
    computeComponentsUsage(processesDetails, processingTypeAndInfoToNonFragmentComponentId)
      .mapValuesNow(usages => usages.map { case (_, nodeIds) => nodeIds.size }.sum)
  }

  def computeComponentsUsage(
      processesDetails: List[ScenarioWithDetailsEntity[ScenarioComponentsUsages]],
      processingTypeAndInfoToNonFragmentComponentId: Map[(ProcessingType, ComponentInfo), ComponentId]
  ): Map[ComponentId, List[(ScenarioWithDetailsEntity[_], List[NodeUsageData])]] = {
    def flattenUsages(processesDetails: List[ScenarioWithDetailsEntity[ScenarioComponentsUsages]]) = for {
      processDetails    <- processesDetails
      componentInfoNode <- processDetails.json.value.toList
      (componentInfo, nodeIds) = componentInfoNode
      componentId = processingTypeAndInfoToNonFragmentComponentId
        .get(processDetails.processingType, componentInfo)
        .getOrElse(
          ComponentId.default(processDetails.processingType, componentInfo)
        ) // the orElse case is for fragments - fragment ids won't be present in the map but must be equal to default
      nodeId <- nodeIds
    } yield ScenarioComponentsUsage[NodeId](componentId, componentInfo, processDetails, nodeId)

    val scenariosComponentUsages        = flattenUsages(processesDetails.filter(_.isFragment == false))
    val fragmentsComponentUsages        = flattenUsages(processesDetails.filter(_.isFragment == true))
    val groupedFragmentsComponentUsages = fragmentsComponentUsages.groupBy(_.processDetails.name)

    val scenarioUsagesWithResolvedFragments: List[ScenarioComponentsUsage[NodeUsageData]] =
      scenariosComponentUsages.flatMap {
        case fragmentUsage @ ScenarioComponentsUsage(
              _,
              ComponentInfo(ComponentType.Fragment, fragmentName),
              processDetails,
              fragmentNodeId
            ) =>
          val fragmentUsageRefined: ScenarioComponentsUsage[NodeUsageData] =
            fragmentUsage.copy(nodeUsageData = ScenarioUsageData(fragmentNodeId))
          val fragmentsUsages: List[ScenarioComponentsUsage[NodeUsageData]] =
            groupedFragmentsComponentUsages.get(ProcessName(fragmentName)).toList.flatten.map {
              case u @ ScenarioComponentsUsage(_, _, _, nodeId: NodeId) =>
                val refinedUsage: ScenarioComponentsUsage[NodeUsageData] =
                  u.copy(processDetails = processDetails, nodeUsageData = FragmentUsageData(fragmentNodeId, nodeId))
                refinedUsage
            }
          fragmentUsageRefined :: fragmentsUsages
        case usageOfOtherComponentType @ ScenarioComponentsUsage(_, _, _, nodeId) =>
          val usageOfOtherComponentTypeRefined: ScenarioComponentsUsage[NodeUsageData] =
            usageOfOtherComponentType.copy(nodeUsageData = ScenarioUsageData(nodeId))
          List(usageOfOtherComponentTypeRefined)
      }

    val fragmentUsages: List[ScenarioComponentsUsage[NodeUsageData]] = fragmentsComponentUsages.flatMap {
      case componentUsage @ ScenarioComponentsUsage(_, _, _, nodeId: NodeId) =>
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
      componentInfo: ComponentInfo,
      processDetails: ScenarioWithDetailsEntity[_],
      nodeUsageData: NodeUsageDataShape
  )

}
