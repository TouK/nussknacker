package pl.touk.nussknacker.engine.process.registrar

import cats.effect.unsafe.IORuntime
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.InterpretationResult
import pl.touk.nussknacker.engine.Interpreter.FutureShape
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.process.ProcessPartFunction
import pl.touk.nussknacker.engine.process.compiler.FlinkProcessCompilerData
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext

import java.util.concurrent.TimeoutException
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal

private[registrar] class SyncInterpretationFunction(val compiledProcessWithDepsProvider: ClassLoader => FlinkProcessCompilerData,
                                                    val node: SplittedNode[_<:NodeData],
                                                    validationContext: ValidationContext, useIOMonad: Boolean)
  extends RichFlatMapFunction[Context, InterpretationResult] with ProcessPartFunction {

  private lazy implicit val ec: ExecutionContext = SynchronousExecutionContext.ctx
  private lazy implicit val runtime: IORuntime = cats.effect.unsafe.implicits.global //TODO: Global is not good idea..

  private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node, validationContext)

  import compiledProcessWithDeps._

  override def flatMap(input: Context, collector: Collector[InterpretationResult]): Unit = {
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

  private def runInterpreter(input: Context): List[Either[InterpretationResult, NuExceptionInfo[_ <: Throwable]]] = {
    //we leave switch to be able to return to Future if IO has some flaws...
    if (useIOMonad) {
      interpreter.interpret(compiledNode, metaData, input).unsafeRunTimed(processTimeout) match {
        case Some(result) => result
        case None => throw new TimeoutException(s"Interpreter is running too long (timeout: $processTimeout)")
      }
    } else {
      implicit val futureShape: FutureShape = new FutureShape()
      Await.result(interpreter.interpret[Future](compiledNode, metaData, input), processTimeout)
    }
  }
}
