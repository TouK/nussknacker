package pl.touk.nussknacker.engine.process

import org.apache.flink.api.common.functions.{RichFlatMapFunction, RichMapFunction}
import org.apache.flink.configuration.Configuration
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult}
import pl.touk.nussknacker.engine.flink.util.ContextInitializingFunction
import pl.touk.nussknacker.engine.flink.util.metrics.InstantRateMeterWithCount
import pl.touk.nussknacker.engine.process.compiler.CompiledProcessWithDeps
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.SynchronousExecutionContext
import pl.touk.nussknacker.engine.util.metrics.RateMeter

import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal

object FlinkProcessRegistrar {

  class SyncInterpretationFunction(val compiledProcessWithDepsProvider: (ClassLoader) => CompiledProcessWithDeps,
                                   node: SplittedNode[_], validationContext: ValidationContext)
    extends RichFlatMapFunction[Context, InterpretationResult] with WithCompiledProcessDeps {

    private lazy implicit val ec: ExecutionContext = SynchronousExecutionContext.ctx
    private lazy val compiledNode = compiledProcessWithDeps.compileSubPart(node, validationContext)
    import compiledProcessWithDeps._

    override def flatMap(input: Context, collector: Collector[InterpretationResult]): Unit = {
      (try {
        Await.result(interpreter.interpret(compiledNode, metaData, input), processTimeout)
      } catch {
        case NonFatal(error) => Right(EspExceptionInfo(None, error, input))
      }) match {
        case Left(ir) =>
          exceptionHandler.handling(None, input)(ir.foreach(collector.collect))
        case Right(info) =>
          exceptionHandler.handle(info)
      }
    }
  }


  class RateMeterFunction[T](groupId: String) extends RichMapFunction[T, T] {
    private var instantRateMeter : RateMeter = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)

      instantRateMeter = InstantRateMeterWithCount.register(getRuntimeContext.getMetricGroup.addGroup(groupId))
    }

    override def map(value: T): T = {
      instantRateMeter.mark()
      value
    }
  }

  case class InitContextFunction(processId: String, taskName: String) extends RichMapFunction[Any, Context] with ContextInitializingFunction {

    override def open(parameters: Configuration): Unit = {
      init(getRuntimeContext)
    }

    override def map(input: Any): Context = newContext.withVariable(Interpreter.InputParamName, input)
  }
}
