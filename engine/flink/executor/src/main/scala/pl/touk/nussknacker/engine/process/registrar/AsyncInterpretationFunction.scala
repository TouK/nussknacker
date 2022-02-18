package pl.touk.nussknacker.engine.process.registrar

import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.functions.async.{ResultFuture, RichAsyncFunction}
import pl.touk.nussknacker.engine.InterpretationResult
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.process.ProcessPartFunction
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters.seqAsJavaListConverter
import scala.util.control.NonFatal
import scala.util.{Failure, Success}

private[registrar] class AsyncInterpretationFunction(val compiledProcessWithDepsProvider: ClassLoader => FlinkProcessCompilerData,
                                                     val node: SplittedNode[_<:NodeData], validationContext: ValidationContext,
                                                     asyncExecutionContextPreparer: AsyncExecutionContextPreparer, useIOMonad: Boolean)
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
    try {
      invokeInterpreter(input) {
        case Right(results) =>
          val exceptions = results.collect { case Right(exInfo) => exInfo }
          val successes = results.collect { case Left(value) => value }
          handleResults(collector, successes, exceptions)
        case Left(ex) =>
          logger.warn("Unexpected error", ex)
          handleResults(collector, Nil, List(NuExceptionInfo(None, ex, input)))
      }
    } catch {
      case NonFatal(ex) =>
        logger.warn("Unexpected error", ex)
        handleResults(collector, Nil, List(NuExceptionInfo(None, ex, input)))
    }


  }

  private def invokeInterpreter(input: Context)
                               (callback: Either[Throwable, List[Either[InterpretationResult, NuExceptionInfo[_ <: Throwable]]]] => Unit): Unit = {
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

  //This function has to be invoked exactly *ONCE* for one asyncInvoke (complete/completeExceptionally) can be invoked only once)
  private def handleResults(collector: ResultFuture[InterpretationResult],
                            results: List[InterpretationResult],
                            exceptions: List[NuExceptionInfo[_ <: Throwable]]): Unit = {
    try {
      exceptions.foreach(exceptionHandler.handle)
      collector.complete(results.asJava)
    } catch {
      case NonFatal(e) =>
        logger.warn("Unexpected exception during exceptionHandler invocation, failing", e)
        collector.completeExceptionally(e)
    }
  }

}
