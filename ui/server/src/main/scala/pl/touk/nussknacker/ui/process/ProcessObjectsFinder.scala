package pl.touk.nussknacker.ui.process

import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectDefinition
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.{ProcessDefinition, QueryableStateName}
import pl.touk.nussknacker.engine.graph.node.{CustomNode, NodeData}
import pl.touk.nussknacker.ui.api.SignalDefinition
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.ProcessDetails
import shapeless.syntax.typeable._

object ProcessObjectsFinder {
  import pl.touk.nussknacker.ui.util.CollectionsEnrichments._

  def findSignals(processes: List[ProcessDetails],
                  definition: ProcessDefinition[ObjectDefinition]): Map[String, SignalDefinition] = {
    definition.signalsWithTransformers.map { case (name, (objDefinition, transformers)) =>
      val processesWithTransformers = findProcessesWithTransformers(processes, transformers)
      name -> SignalDefinition(name, objDefinition.parameters.map(_.name), processesWithTransformers)
    }
  }

  def findQueries(processes: List[ProcessDetails],
                  definition: ProcessDefinition[ObjectDefinition]): Map[QueryableStateName, List[String]] = {
    definition.customStreamTransformers.mapValues(_._2.queryableStateNames)
      .sequenceMap
      .mapValues(transformers => findProcessesWithTransformers(processes, transformers.toSet))
  }

  private def findProcessesWithTransformers(processList: List[ProcessDetails], transformers: Set[String]): List[String] = {
    processList
      .flatMap(_.json.toList)
      .filter(processContainsData(nodeIsSignalTransformer(transformers))).map(_.id)
  }


  private def nodeIsSignalTransformer(transformers: Set[String])(node: NodeData) = {
    def isCustomNodeFromList = (c:CustomNode) => transformers.contains(c.nodeType)
    node.cast[CustomNode].exists(isCustomNodeFromList)
  }

  private def processContainsData(predicate: NodeData=>Boolean)(process: DisplayableProcess) : Boolean = {
    process.nodes.exists(predicate)
  }
}