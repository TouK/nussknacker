package pl.touk.nussknacker.engine.process.registrar

import cats.effect.IO
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.InterpretationResult
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.api.ScenarioProcessingContext
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process.ServiceExecutionContext
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.process.ProcessPartFunction
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime.syncEc

import java.util.concurrent.TimeoutException
import scala.concurrent.{Await, Future}
import scala.util.control.NonFatal

private[registrar] class SyncInterpretationFunction(
    val compiledProcessWithDepsProvider: ClassLoader => FlinkProcessCompilerData,
    val node: SplittedNode[_ <: NodeData],
    validationContext: ValidationContext,
    useIOMonad: Boolean
) extends RichFlatMapFunction[ScenarioProcessingContext, InterpretationResult]
    with ProcessPartFunction {

  private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node, validationContext)
  private lazy val serviceExecutionContext: ServiceExecutionContext = ServiceExecutionContext(syncEc)

  import SynchronousExecutionContextAndIORuntime._
  import compiledProcessWithDeps._

  override def flatMap(input: ScenarioProcessingContext, collector: Collector[InterpretationResult]): Unit = {
    (try {
      runInterpreter(input)
    } catch {
      case NonFatal(error) => List(Right(NuExceptionInfo(None, error, input)))
    }).foreach {
      case Left(ir) =>
        collector.collect(ir)
      case Right(info) =>
        exceptionHandler.handle(info)
    }
  }

  private def runInterpreter(
      input: ScenarioProcessingContext
  ): List[Either[InterpretationResult, NuExceptionInfo[_ <: Throwable]]] = {
    // we leave switch to be able to return to Future if IO has some flaws...
    if (useIOMonad) {
      val resultOpt = interpreter
        .interpret[IO](compiledNode, metaData, input, serviceExecutionContext)
        .unsafeRunTimed(processTimeout)
      resultOpt match {
        case Some(result) => result
        case None         => throw new TimeoutException(s"Interpreter is running too long (timeout: $processTimeout)")
      }
    } else {
      Await.result(
        awaitable = interpreter.interpret[Future](compiledNode, metaData, input, serviceExecutionContext),
        atMost = processTimeout
      )
    }
  }

}
