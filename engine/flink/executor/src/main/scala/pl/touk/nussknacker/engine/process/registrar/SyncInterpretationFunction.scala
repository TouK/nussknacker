package pl.touk.nussknacker.engine.process.registrar

import cats.effect.IO
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
    with ProcessPartFunction {

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
      case NonFatal(error) => List(Right(NuExceptionInfo(Some(nodeComponentInfo), error, input)))
    }).foreach {
      case Left(ir) =>
        collector.collect(ir)
      case Right(info) =>
        exceptionHandler.handle(info)
    }
  }

  private def runInterpreter(
      input: Context
  ): List[Either[InterpretationResult, NuExceptionInfo]] = {
    // we leave switch to be able to return to Future if IO has some flaws...
    if (useIOMonad) {
      compilerData.interpreter
        .interpret[IO](compiledNode, compilerData.jobData, input, serviceExecutionContext)
        .unsafeRunTimedAttempt(compilerData.processTimeout) match {
        case Right(result) =>
          result
        case Left(Error.Interrupted) =>
          throw new RuntimeException(s"Interpreter action was interrupted")
        case Left(Error.Timeout) =>
          throw new TimeoutException(s"Interpreter is running too long (timeout: ${compilerData.processTimeout})")
      }
    } else {
      Await.result(
        awaitable = compilerData.interpreter
          .interpret[Future](compiledNode, compilerData.jobData, input, serviceExecutionContext),
        atMost = compilerData.processTimeout
      )
    }
  }

}
