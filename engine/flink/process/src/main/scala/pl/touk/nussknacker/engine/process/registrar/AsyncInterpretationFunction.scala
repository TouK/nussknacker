package pl.touk.nussknacker.engine.process.registrar

import java.util.Collections

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.async.{ResultFuture, RichAsyncFunction}
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.process.ProcessPartFunction
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode

import scala.collection.JavaConverters._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.{Failure, Success}
import scala.util.control.NonFatal

private[registrar] class AsyncInterpretationFunction(val compiledProcessWithDepsProvider: ClassLoader => FlinkProcessCompilerData,
                                                     val node: SplittedNode[_<:NodeData],
                                                     val validationContext: ValidationContext,
                                                     asyncExecutionContextPreparer: AsyncExecutionContextPreparer,
                                                     useIOMonad: Boolean)
  extends RichAsyncFunction[Context, InterpretationResult] with LazyLogging with ProcessPartFunction {

  import compiledProcessWithDeps._

  private var executionContext: ExecutionContext = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    executionContext = asyncExecutionContextPreparer.prepareExecutionContext(compiledProcessWithDeps.metaData.id,
      getRuntimeContext.getExecutionConfig.getParallelism)
  }

  override def asyncInvoke(input: Context, collector: ResultFuture[InterpretationResult]): Unit = {
    try {
      invokeInterpreter(input) {
        case Right(Left(result)) => collector.complete(result.asJava)
        case Right(Right(exInfo)) => handleException(collector, exInfo)
        case Left(ex) =>
          logger.warn("Unexpected error", ex)
          handleException(collector, EspExceptionInfo(None, ex, input))
      }
    } catch {
      case NonFatal(ex) =>
        logger.warn("Unexpected error", ex)
        handleException(collector, EspExceptionInfo(None, ex, input))
    }
  }

  private def invokeInterpreter(input: Context)
                               (callback: Either[Throwable, Either[List[InterpretationResult], EspExceptionInfo[_ <: Throwable]]] => Unit): Unit = {
    implicit val ec: ExecutionContext = executionContext
    //we leave switch to be able to return to Future if IO has some flaws...
    if (useIOMonad) {
      interpreter.interpret[IO](compiledNode, metaData, input).unsafeRunAsync(callback)
    } else {
      implicit val future: FutureShape = new FutureShape()
      interpreter.interpret[Future](compiledNode, metaData, input).onComplete {
        //use result.toEither after dropping Scala 2.11 support
        case Success(a) => callback(Right(a))
        case Failure(a) => callback(Left(a))
      }
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
