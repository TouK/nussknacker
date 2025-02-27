package pl.touk.nussknacker.engine.process.registrar

import pl.touk.nussknacker.engine.api.StreamMetaData
import pl.touk.nussknacker.engine.api.async.{DefaultAsyncInterpretationValue, DefaultAsyncInterpretationValueDeterminer}
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer
import pl.touk.nussknacker.engine.flink.api.ConfigGlobalParameters
import pl.touk.nussknacker.engine.graph.node.{Enricher, NodeData, Processor}
import pl.touk.nussknacker.engine.splittedgraph.{splittednode, SplittedNodesCollector}

private[registrar] case class AsyncInterpretationDeterminer(
    configParameters: Option[ConfigGlobalParameters],
    asyncExecutionContextPreparer: AsyncExecutionContextPreparer
) {

  def determine(splittedNode: splittednode.SplittedNode[NodeData], streamMetaData: StreamMetaData): Boolean = {
    isAsyncEnabled(streamMetaData) && !shouldForceSyncInterpretation(splittedNode)
  }

  private def isAsyncEnabled(streamMetaData: StreamMetaData): Boolean = {
    val defaultAsync: DefaultAsyncInterpretationValue =
      DefaultAsyncInterpretationValueDeterminer.determine(asyncExecutionContextPreparer)
    val asyncEnabled = streamMetaData.useAsyncInterpretation.getOrElse(defaultAsync.value)
    asyncEnabled
  }

  private def shouldForceSyncInterpretation(splittedNode: splittednode.SplittedNode[NodeData]): Boolean = {
    val forceSyncInterpretationEnabled =
      configParameters.flatMap(_.forceSyncInterpretationForSyncScenarioPart).getOrElse(true)
    forceSyncInterpretationEnabled && !containsServices(splittedNode)
  }

  private def containsServices(splittedNode: splittednode.SplittedNode[NodeData]): Boolean = {
    val nodes = SplittedNodesCollector.collectNodes(splittedNode).map(_.data)
    nodes.exists {
      case _: Enricher                    => true
      case Processor(_, _, isDisabled, _) => !isDisabled.getOrElse(false)
      case _                              => false
    }
  }

}
