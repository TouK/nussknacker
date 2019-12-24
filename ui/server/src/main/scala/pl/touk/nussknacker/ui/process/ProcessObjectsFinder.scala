package pl.touk.nussknacker.ui.process

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ProcessDefinition, QueryableStateName}
import pl.touk.nussknacker.engine.graph
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.ui.api.SignalDefinition
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.processdetails.ProcessDetails
import shapeless.syntax.typeable._

object ProcessObjectsFinder {
  import pl.touk.nussknacker.ui.util.CollectionsEnrichments._
  import pl.touk.nussknacker.engine.util.Implicits._

  def findSignals(processes: List[ProcessDetails],
                  definitions: Iterable[ProcessDefinition[ObjectDefinition]]): Map[String, SignalDefinition] = definitions.flatMap { definition =>
    definition.signalsWithTransformers.map { case (name, (objDefinition, transformers)) =>
      val processesWithTransformers = findProcessesWithTransformers(processes, transformers)
      name -> SignalDefinition(name, objDefinition.parameters.map(_.name), processesWithTransformers)
    }
  }.toMap

  def findQueries(processes: List[ProcessDetails],
                  definitions: Iterable[ProcessDefinition[ObjectDefinition]]): Map[QueryableStateName, List[String]] = {

    definitions.flatMap { definition =>
      definition.customStreamTransformers.mapValuesNow(_._2.queryableStateNames)
           .sequenceMap
           .mapValuesNow(transformers => findProcessesWithTransformers(processes, transformers.toSet))
    }.toMap
  }

  //TODO return Map[ProcessingType, List[String]]?
  def findUnusedComponents(processes: List[ProcessDetails],
                           processDefinitions: List[ProcessDefinition[ObjectDefinition]]): List[String] = {
    val extracted = extractProcesses(processes.flatMap(_.json))
    val subprocessIds = extracted.subprocessesOnly.map(_.id)
    val allNodes = extracted.allProcesses.flatMap(_.nodes)
    val allObjectIds = componentIds(processDefinitions, subprocessIds)
    val usedObjectIds = allNodes.collect { case n: graph.node.WithComponent => n.componentId }.distinct
    allObjectIds.diff(usedObjectIds).sortCaseInsensitive
  }

  def findComponents(processes: List[ProcessDetails], componentId: String): List[ProcessComponent] = {
    processes.flatMap(processDetails => processDetails.json match {
      case Some(process) => process.nodes.collect {
        case node:WithComponent if node.componentId == componentId => ProcessComponent(
          processName = processDetails.name,
          nodeId = node.id,
          processCategory = processDetails.processCategory,
          isDeployed = processDetails.isDeployed
        )
      }
      case None => Nil
    })
  }

  def componentIds(processDefinitions: List[ProcessDefinition[ObjectDefinition]], subprocessIds: List[String]): List[String] = {
    val ids = processDefinitions.flatMap(_.componentIds)
    (ids ++ subprocessIds).distinct.sortCaseInsensitive
  }

  //TODO it will work for single depth subprocesses only - i.e it won't find for transformer inside subprocess that is inside subprocess
  private def findProcessesWithTransformers(processList: List[ProcessDetails], transformers: Set[String]): List[String] = {
    val extracted = extractProcesses(processList.flatMap(_.json))
    val processesWithTransformers = extracted.processesOnly.filter(processContainsData(nodeIsSignalTransformer(transformers)))
    val subprocessesWithTransformers = extracted.subprocessesOnly.filter(processContainsData(nodeIsSignalTransformer(transformers))).map(_.id)
    val processesThatContainsSubprocessWithTransformer = extracted.allProcesses.filter { proc =>
      processContainsData(node => graph.node.asSubprocessInput(node).exists(sub => subprocessesWithTransformers.contains(sub.componentId)))(proc)
    }
    (processesWithTransformers ++ processesThatContainsSubprocessWithTransformer).map(_.id).distinct.sortCaseInsensitive
  }

  private def nodeIsSignalTransformer(transformers: Set[String])(node: NodeData): Boolean = {
    def isCustomNodeFromList = (c:CustomNode) => transformers.contains(c.nodeType)
    graph.node.asCustomNode(node).exists(isCustomNodeFromList)
  }

  private def processContainsData(predicate: NodeData => Boolean)(process: DisplayableProcess) : Boolean = {
    process.nodes.exists(predicate)
  }

  private def extractProcesses(displayableProcesses: List[DisplayableProcess]): ExtractedProcesses = {
    ExtractedProcesses(displayableProcesses)
  }

  private case class ExtractedProcesses(allProcesses: List[DisplayableProcess]) {
    val processesOnly =  allProcesses.filter(!_.properties.isSubprocess)
    val subprocessesOnly = allProcesses.filter(_.properties.isSubprocess)
  }
}

@JsonCodec case class ProcessComponent(processName: String, nodeId: String, processCategory: String, isDeployed: Boolean)
