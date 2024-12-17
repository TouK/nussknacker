package pl.touk.nussknacker.engine.process

import org.apache.flink.api.common.functions.{OpenContext, RichFunction}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.splittedgraph.SplittedNodesCollector
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode

//Helper trait to deal with lifecycle of single process part (e.g. handling open/close)
trait ProcessPartFunction extends ExceptionHandlerFunction {

  protected def node: SplittedNode[_ <: NodeData]

  private val nodesUsed = SplittedNodesCollector.collectNodes(node).map(_.data)

  override def close(): Unit = {
    super.close()
    if (compilerData != null) {
      compilerData.close(nodesUsed)
    }
  }

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    compilerData.open(getRuntimeContext, nodesUsed)
  }

}

//Helper trait dealing with ExceptionHandler lifecycle
trait ExceptionHandlerFunction extends RichFunction {

  def compilerDataForClassloader: ClassLoader => FlinkProcessCompilerData

  protected var exceptionHandler: FlinkExceptionHandler = _

  protected lazy val compilerData: FlinkProcessCompilerData = compilerDataForClassloader(
    getRuntimeContext.getUserCodeClassLoader
  )

  override def close(): Unit = {
    if (exceptionHandler != null) {
      exceptionHandler.close()
    }
  }

  override def open(openContext: OpenContext): Unit = {
    exceptionHandler = compilerData.prepareExceptionHandler(getRuntimeContext)
  }

}
