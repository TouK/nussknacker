package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.restmodel.component.{ComponentIdParts, NodeId, ScenarioComponentsUsages}
import pl.touk.nussknacker.restmodel.processdetails.BaseProcessDetails

import scala.annotation.tailrec

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

    @tailrec
    def mergeFragments(fragmentsProcessesDetails: List[BaseProcessDetails[ScenarioComponentsUsages]],
                       acc: Map[ComponentId, List[(BaseProcessDetails[Unit], List[NodeId])]]): Map[ComponentId, List[(BaseProcessDetails[Unit], List[NodeId])]] =
      fragmentsProcessesDetails match {
        case Nil => acc
        case fragmentProcessesDetails :: t => {
          val componentIdUsages = toComponentIdUsages(fragmentProcessesDetails).toMap

          val newAcc = acc.toList.map {
            case (componentId, componentNodeIds) =>
              componentIdUsages.get(componentId) match {
                case None => (componentId, componentNodeIds)
                case Some((_, fragmentNodeIds)) => (componentId, componentNodeIds.map {
                  case (baseProcessDetails, scenarioNodeIds) => (baseProcessDetails, scenarioNodeIds ++ fragmentNodeIds)
                })
              }
          }.toMap


          mergeFragments(t, newAcc)
        }
      }

    val scenariosProcessesDetails = processesDetails.filter(_.isSubprocess == false)
    val fragmentsProcessesDetails = processesDetails.filter(_.isSubprocess == true)

    val scenarioComponentsUsages = scenariosProcessesDetails
      .flatMap(toComponentIdUsages)
      // Can be replaced with .groupMap from Scala 2.13.
      .groupBy { case (componentId, _) => componentId }
      .transform { case (_, usages) => usages.map { case (_, processDetails) => processDetails } }

    mergeFragments(fragmentsProcessesDetails, scenarioComponentsUsages)
  }





}
