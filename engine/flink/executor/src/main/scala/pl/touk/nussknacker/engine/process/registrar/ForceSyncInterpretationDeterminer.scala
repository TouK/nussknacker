package pl.touk.nussknacker.engine.process.registrar

import pl.touk.nussknacker.engine.flink.api.ConfigGlobalParameters
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData, Processor}
import pl.touk.nussknacker.engine.splittedgraph.{SplittedNodesCollector, splittednode}

private[registrar] case class ForceSyncInterpretationDeterminer(configParameters: Option[ConfigGlobalParameters]) {

  def forNode(splittedNode: splittednode.SplittedNode[NodeData]): Boolean = {
    val forceSyncInterpretationEnabled = configParameters.flatMap(_.forceSyncInterpretationForSyncScenarioPart).getOrElse(true)
    forceSyncInterpretationEnabled && !containsServices(splittedNode)
  }

  private def containsServices(splittedNode: splittednode.SplittedNode[NodeData]): Boolean = {
    val nodes = SplittedNodesCollector.collectNodes(splittedNode).map(_.data)
    nodes.exists {
      case _: Enricher => true
      case Processor(_, _, isDisabled, _) => !isDisabled.getOrElse(false)
      case _ => false
    }
  }

}
