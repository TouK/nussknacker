package pl.touk.nussknacker.ui.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.api.component.ComponentId
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.graph
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import pl.touk.nussknacker.ui.component.ComponentIdProvider

object ProcessObjectsFinder {

  import pl.touk.nussknacker.engine.util.Implicits._

  def computeComponentsUsageCount(componentIdProvider: ComponentIdProvider, processes: List[ProcessDetails]): Map[ComponentId, Long] =
    extractProcesses(processes.map(_.json))
      .allProcesses
      .flatMap(process => process.nodes.flatMap(componentIdProvider.nodeToComponentId(process.processingType, _)))
      .groupBy(identity)
      .mapValues(_.size)

  def computeComponentsUsage(componentIdProvider: ComponentIdProvider, processes: List[ProcessDetails]): Map[ComponentId, List[(ProcessDetails, List[String])]] =
    processes.flatMap(processDetails =>
      processDetails.json.nodes.flatMap(node =>
        componentIdProvider.nodeToComponentId(processDetails.processingType, node)
          .map((_, node.id, processDetails))
      ))
      .groupBy(_._1)
      .map{case(componentId, groupedByComponentId) =>
        (
          componentId,
          groupedByComponentId
            .groupBy(_._3)
            .toList
            .map{
              case (process, groupedByProcess) => (process, groupedByProcess.map(_._2).sorted)
            }
            .sortBy(_._1.name)
        )
      }

  def componentIds(processDefinitions: List[ProcessDefinition[ObjectDefinition]], subprocessIds: List[String]): List[String] = {
    val ids = processDefinitions.flatMap(_.componentIds)
    (ids ++ subprocessIds).distinct.sortCaseInsensitive
  }

  //TODO it will work for single depth subprocesses only - i.e it won't find for transformer inside subprocess that is inside subprocess
  private def findProcessesWithTransformers(processList: List[ProcessDetails], transformers: Set[String]): List[String] = {
    val extracted = extractProcesses(processList.map(_.json))
    val processesWithTransformers = extracted.processesOnly.filter(processContainsData(nodeIsSignalTransformer(transformers)))
    val subprocessesWithTransformers = extracted.subprocessesOnly.filter(processContainsData(nodeIsSignalTransformer(transformers))).map(_.id)
    val processesThatContainsSubprocessWithTransformer = extracted.allProcesses.filter { proc =>
      processContainsData(node => graph.node.asSubprocessInput(node).exists(sub => subprocessesWithTransformers.contains(sub.componentId)))(proc)
    }
    (processesWithTransformers ++ processesThatContainsSubprocessWithTransformer).map(_.id).distinct.sortCaseInsensitive
  }

  private def nodeIsSignalTransformer(transformers: Set[String])(node: NodeData): Boolean = {
    def isCustomNodeFromList = (c: CustomNode) => transformers.contains(c.nodeType)

    graph.node.asCustomNode(node).exists(isCustomNodeFromList)
  }

  private def processContainsData(predicate: NodeData => Boolean)(process: DisplayableProcess): Boolean = {
    process.nodes.exists(predicate)
  }

  private def extractProcesses(displayableProcesses: List[DisplayableProcess]): ExtractedProcesses = {
    ExtractedProcesses(displayableProcesses)
  }

  private case class ExtractedProcesses(allProcesses: List[DisplayableProcess]) {
    val processesOnly = allProcesses.filter(!_.properties.isSubprocess)
    val subprocessesOnly = allProcesses.filter(_.properties.isSubprocess)
  }
}

@JsonCodec case class ProcessComponent(processName: String, nodeId: String, processCategory: String, isDeployed: Boolean)
