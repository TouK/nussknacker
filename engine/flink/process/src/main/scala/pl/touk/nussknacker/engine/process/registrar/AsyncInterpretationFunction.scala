package pl.touk.nussknacker.engine.process.registrar

import java.util.Collections

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.async.{ResultFuture, RichAsyncFunction}
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.process.ProcessPartFunction
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.splittedgraph.SplittedNodesCollector
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

private[registrar] class AsyncInterpretationFunction(val compiledProcessWithDepsProvider: ClassLoader => FlinkProcessCompilerData,
                                                     val node: SplittedNode[_<:NodeData], validationContext: ValidationContext,
                                                     asyncExecutionContextPreparer: AsyncExecutionContextPreparer)
  extends RichAsyncFunction[Context, InterpretationResult] with LazyLogging with ProcessPartFunction {

  private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node, validationContext)

  import compiledProcessWithDeps._

  private var executionContext: ExecutionContext = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    executionContext = asyncExecutionContextPreparer.prepareExecutionContext(compiledProcessWithDeps.metaData.id,
      getRuntimeContext.getExecutionConfig.getParallelism)
  }

  override def asyncInvoke(input: Context, collector: ResultFuture[InterpretationResult]): Unit = {
    implicit val ec: ExecutionContext = executionContext
    try {
      interpreter.interpret(compiledNode, metaData, input)
        .onComplete {
          case Success(Left(result)) => collector.complete(result.asJava)
          case Success(Right(exInfo)) => handleException(collector, exInfo)
          case Failure(ex) =>
            logger.warn("Unexpected error", ex)
            handleException(collector, EspExceptionInfo(None, ex, input))
        }
    } catch {
      case NonFatal(ex) =>
        logger.warn("Unexpected error", ex)
        handleException(collector, EspExceptionInfo(None, ex, input))
    }
  }

  override def close(): Unit = {
    super.close()
    asyncExecutionContextPreparer.close()
  }

  private def handleException(collector: ResultFuture[InterpretationResult], info: EspExceptionInfo[_ <: Throwable]): Unit = {
    try {
      exceptionHandler.handle(info)
      collector.complete(Collections.emptyList[InterpretationResult]())
    } catch {
      case NonFatal(e) => logger.warn("Unexpected fail, refusing to collect??", e); collector.completeExceptionally(e)
    }
  }

}
