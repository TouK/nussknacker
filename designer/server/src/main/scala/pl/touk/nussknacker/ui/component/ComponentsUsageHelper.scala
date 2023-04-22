package pl.touk.nussknacker.ui.component

import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.component.ComponentUtil
import pl.touk.nussknacker.restmodel.component.NodeId
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.process.repository.{ComponentIdParts, ScenarioComponentsUsages}

object ComponentsUsageHelper {

  import pl.touk.nussknacker.engine.util.Implicits._

  def computeScenarioUsages(scenario: CanonicalProcess): ScenarioComponentsUsages = {
    val usagesList = for {
      node <- scenario.collectAllNodes
      componentType <- ComponentUtil.extractComponentType(node)
      componentName = ComponentUtil.extractComponentName(node)
    } yield {
      (componentName, componentType, node.id)
    }
    val usagesMap = usagesList
      .groupMap({ case (componentName, componentType, _) => ComponentIdParts(componentName, componentType) })({ case (_, _, nodeIds) => nodeIds })
    ScenarioComponentsUsages(usagesMap)
  }

  def computeComponentsUsageCount(componentIdProvider: ComponentIdProvider, processes: List[ProcessDetails]): Map[ComponentId, Long] =
    processes
      .flatMap(processDetails => extractComponentIds(componentIdProvider, processDetails))
      .groupBy(identity)
      .mapValuesNow(_.size)

  def computeComponentsUsage(componentIdProvider: ComponentIdProvider, processes: List[ProcessDetails]): Map[ComponentId, List[(ProcessDetails, List[NodeId])]] =
    processes
      .flatMap(processDetails => extractComponentIdsWithProcessAndNodeId(componentIdProvider, processDetails))
      .groupBy { case (componentId, _, _) => componentId }
      .map { case (componentId, groupedByComponentId) =>
        val processAndNodeList = groupedByComponentId.map { case (_, processDetails, nodeId) => (processDetails, nodeId) }
        val groupedByProcess = groupByProcess(processAndNodeList)
        (componentId, groupedByProcess)
      }

  private def extractComponentIds(componentIdProvider: ComponentIdProvider, processDetails: ProcessDetails): List[ComponentId] = {
    processDetails.json.nodes.flatMap(componentIdProvider.nodeToComponentId(processDetails.processingType, _))
  }

  private def extractComponentIdsWithProcessAndNodeId(componentIdProvider: ComponentIdProvider, processDetails: ProcessDetails): List[(ComponentId, ProcessDetails, NodeId)] = {
    processDetails.json.nodes.flatMap(node =>
      componentIdProvider.nodeToComponentId(processDetails.processingType, node)
        .map((_, processDetails, node.id))
    )
  }

  private def groupByProcess(processAndNodeList: List[(ProcessDetails, NodeId)]): List[(ProcessDetails, List[NodeId])] = {
    processAndNodeList
      .groupBy { case (processDetails, _) => processDetails }
      .toList
      .map {
        case (processDetails, groupedByProcess) =>
          val nodeIds = groupedByProcess.map { case (_, nodeId) => nodeId }.sorted
          (processDetails, nodeIds)
      }
      .sortBy { case (processDetails, _) => processDetails.name }
  }

}
