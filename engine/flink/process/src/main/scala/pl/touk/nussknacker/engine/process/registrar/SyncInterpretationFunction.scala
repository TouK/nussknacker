package pl.touk.nussknacker.engine.process.registrar

import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.process.WithCompiledProcessDeps
import pl.touk.nussknacker.engine.process.compiler.CompiledProcessWithDeps
import pl.touk.nussknacker.engine.splittedgraph.SplittedNodesCollector
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal

private[registrar] class SyncInterpretationFunction(val compiledProcessWithDepsProvider: (ClassLoader) => CompiledProcessWithDeps,
                                 node: SplittedNode[_<:NodeData], validationContext: ValidationContext)
  extends RichFlatMapFunction[Context, InterpretationResult] with WithCompiledProcessDeps {

  private lazy implicit val ec: ExecutionContext = SynchronousExecutionContext.ctx
  private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node, validationContext)

  import compiledProcessWithDeps._

  override def nodesUsed: List[NodeData] = SplittedNodesCollector.collectNodes(node).map(_.data)

  override def flatMap(input: Context, collector: Collector[InterpretationResult]): Unit = {
    (try {
      Await.result(interpreter.interpret(compiledNode, metaData, input), processTimeout)
    } catch {
      case NonFatal(error) => Right(EspExceptionInfo(None, error, input))
    }) match {
      case Left(ir) =>
        ir.foreach(collector.collect)
      case Right(info) =>
        exceptionHandler.handle(info)
    }
  }
}
