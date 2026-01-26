package pl.touk.nussknacker.engine.process.registrar

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid}
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.InterpretationResult
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.ServiceExecutionContext
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.process.ProcessPartFunction
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.process.util.IoUtils._
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime.syncEc

import java.util.concurrent.TimeoutException
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

private[registrar] class SyncInterpretationFunction(
    val compilerDataForClassloader: ClassLoader => FlinkProcessCompilerData,
    val node: SplittedNode[_ <: NodeData],
    validationContext: ValidationContext,
    nodeComponentInfo: NodeComponentInfo,
    useIOMonad: Boolean
) extends RichFlatMapFunction[Context, InterpretationResult]
    with ProcessPartFunction
    with LazyLogging {

  private lazy val compiledNode =
    compilerData.compileSubPart(
      node = node,
      validationContext = validationContext,
      // nodes compiled for interpreter purpose don't have access to stream execution environment
      engineCompilationDeps = EngineScenarioCompilationDependencies.empty
    )

  private lazy val serviceExecutionContext: ServiceExecutionContext = ServiceExecutionContext(syncEc)

  import SynchronousExecutionContextAndIORuntime._

  override def flatMap(input: Context, collector: Collector[InterpretationResult]): Unit = {
    (try {
      runInterpreter(input)
    } catch {
      case NonFatal(error) => Valid(singleExceptionInfo(error, input))
    }) match {
      case Valid(interpretationResults) =>
        interpretationResults.foreach {
          case Left(ir) =>
            collector.collect(ir)
          case Right(info) =>
            exceptionHandler.handle(info)
        }
      case Invalid(InterruptedError) =>
        // The running scenario was canceled, causing an interruption.
        // This is normal control flow, not an interpretation error, so it is handled silently (logged at debug level for diagnostic purposes).
        logger.debug(s"Interpreter action was interrupted. ContextId: ${input.id.legacyString}")
    }
  }

  private def runInterpreter(
      input: Context
  ): Validated[InterruptedError.type, List[Either[InterpretationResult, NuExceptionInfo]]] = {
    // we leave switch to be able to return to Future if IO has some flaws...
    if (useIOMonad) {
      compilerData.interpreter
        .interpret[IO](compiledNode, compilerData.jobData, input, serviceExecutionContext)
        .unsafeRunTimedAttempt(compilerData.processTimeout) match {
        case Right(result) =>
          Valid(result)
        case Left(Error.Interrupted) =>
          Invalid(InterruptedError)
        case Left(Error.Timeout) =>
          Valid(
            singleExceptionInfo(
              new TimeoutException(
                s"Interpreter is running too long (timeout: ${compilerData.processTimeout})"
              ),
              input
            )
          )
      }
    } else {
      Valid(
        Await.result(
          awaitable = compilerData.interpreter
            .interpret[Future](compiledNode, compilerData.jobData, input, serviceExecutionContext),
          atMost = compilerData.processTimeout
        )
      )
    }
  }

  private def singleExceptionInfo(
      error: Throwable,
      input: Context
  ): List[Either[InterpretationResult, NuExceptionInfo]] = {
    List(Right(NuExceptionInfo(Some(nodeComponentInfo), error, input)))
  }

  private case object InterruptedError

}
