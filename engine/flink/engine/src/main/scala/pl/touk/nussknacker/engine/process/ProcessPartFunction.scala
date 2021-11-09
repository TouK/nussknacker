package pl.touk.nussknacker.engine.process

import org.apache.flink.api.common.functions.{RichFunction, RuntimeContext}
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.splittedgraph.SplittedNodesCollector
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode

//Helper trait to deal with lifecycle of single process part (e.g. handling open/close)
trait ProcessPartFunction extends ExceptionHandlerFunction {

  protected def node: SplittedNode[_<:NodeData]

  private val nodesUsed = SplittedNodesCollector.collectNodes(node).map(_.data)

  override def close(): Unit = {
    super.close()
    if (compiledProcessWithDeps != null) {
      compiledProcessWithDeps.close(nodesUsed)
    }
  }

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    compiledProcessWithDeps.open(getRuntimeContext, nodesUsed)
  }

}

//Helper trait dealing with ExceptionHandler lifecycle
trait ExceptionHandlerFunction extends RichFunction {

  def compiledProcessWithDepsProvider: ClassLoader => FlinkProcessCompilerData

  protected var exceptionHandler: FlinkEspExceptionHandler = _

  def exceptionHandlerPreparer: RuntimeContext => FlinkEspExceptionHandler

  protected lazy val compiledProcessWithDeps : FlinkProcessCompilerData = compiledProcessWithDepsProvider(getRuntimeContext.getUserCodeClassLoader)

  override def close(): Unit = {
    if (exceptionHandler != null) {
      exceptionHandler.close()
    }
  }

  override def open(parameters: Configuration): Unit = {
    exceptionHandler = exceptionHandlerPreparer(getRuntimeContext)
  }

}
