package pl.touk.nussknacker.engine.process.registrar

import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.OpenContext
import org.apache.flink.streaming.api.functions.async.{ResultFuture, RichAsyncFunction}
import pl.touk.nussknacker.engine.InterpretationResult
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.{AsyncExecutionContextPreparer, ServiceExecutionContext}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.process.ProcessPartFunction
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime

import scala.concurrent.{ExecutionContext, Future}
import scala.jdk.CollectionConverters._
import scala.util.control.NonFatal

private[registrar] class AsyncInterpretationFunction(
    val compilerDataForClassloader: ClassLoader => FlinkProcessCompilerData,
    val node: SplittedNode[_ <: NodeData],
    validationContext: ValidationContext,
    serviceExecutionContextPreparer: AsyncExecutionContextPreparer,
    useIOMonad: Boolean
) extends RichAsyncFunction[Context, InterpretationResult]
    with LazyLogging
    with ProcessPartFunction {

  private lazy val compiledNode = compilerData.compileSubPart(node, validationContext)

  private var serviceExecutionContext: ServiceExecutionContext = _

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)

    getRuntimeContext.registerUserCodeClassLoaderReleaseHookIfAbsent(
      "closeAsyncExecutionContext",
      () => {
        logger.info("User class loader release hook called - closing async execution context")
        serviceExecutionContextPreparer.close()
      }
    )

    serviceExecutionContext = serviceExecutionContextPreparer.prepare(compilerData.jobData.metaData.name)
  }

  override def asyncInvoke(input: Context, collector: ResultFuture[InterpretationResult]): Unit = {
    try {
      invokeInterpreter(input) {
        case Right(results) =>
          val exceptions = results.collect { case Right(exInfo) => exInfo }
          val successes  = results.collect { case Left(value) => value }
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

  private def invokeInterpreter(
      input: Context
  )(callback: Either[Throwable, List[Either[InterpretationResult, NuExceptionInfo[_ <: Throwable]]]] => Unit): Unit = {
    // we leave switch to be able to return to Future if IO has some flaws...
    if (useIOMonad) {
      implicit val ioRuntime: IORuntime = SynchronousExecutionContextAndIORuntime.syncIoRuntime
      compilerData.interpreter
        .interpret[IO](compiledNode, compilerData.jobData, input, serviceExecutionContext)
        .unsafeRunAsync(callback)
    } else {
      implicit val executionContext: ExecutionContext = SynchronousExecutionContextAndIORuntime.syncEc
      compilerData.interpreter
        .interpret[Future](compiledNode, compilerData.jobData, input, serviceExecutionContext)
        .onComplete { result => callback(result.toEither) }
    }
  }

  override def close(): Unit = {
    super.close()
  }

  // This function has to be invoked exactly *ONCE* for one asyncInvoke (complete/completeExceptionally) can be invoked only once)
  private def handleResults(
      collector: ResultFuture[InterpretationResult],
      results: List[InterpretationResult],
      exceptions: List[NuExceptionInfo[_ <: Throwable]]
  ): Unit = {
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
