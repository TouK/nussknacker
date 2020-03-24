package pl.touk.nussknacker.engine.process

import com.typesafe.scalalogging.LazyLogging
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.functions.{RichFlatMapFunction, RichMapFunction}
import org.apache.flink.configuration.Configuration
import org.apache.flink.runtime.execution.librarycache.FlinkUserCodeClassLoaders
import org.apache.flink.streaming.api.environment.RemoteStreamEnvironment
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.test.TestRunId
import pl.touk.nussknacker.engine.api.{Context, InterpretationResult, ProcessVersion}
import pl.touk.nussknacker.engine.flink.util.ContextInitializingFunction
import pl.touk.nussknacker.engine.flink.util.metrics.{InstantRateMeterWithCount, MetricUtils}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.process.compiler.CompiledProcessWithDeps
import pl.touk.nussknacker.engine.process.util.Serializers
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.{SynchronousExecutionContext, ThreadUtils}
import pl.touk.nussknacker.engine.util.metrics.RateMeter

import scala.concurrent.{Await, ExecutionContext}
import scala.util.control.NonFatal

trait FlinkProcessRegistrar[Env] extends LazyLogging {

  protected def isRemoteEnv(env: Env): Boolean

  protected def prepareExecutionConfig(config: ExecutionConfig,
                                       enableObjectReuse: Boolean): Unit = {
    Serializers.registerSerializers(config)
    if (enableObjectReuse) {
      config.enableObjectReuse()
      logger.debug("Object reuse enabled")
    }
  }

  protected def usingRightClassloader(env: Env)(action: => Unit): Unit = {
    if (!isRemoteEnv(env)) {
      val flinkLoaderSimulation =  FlinkUserCodeClassLoaders.childFirst(Array.empty, Thread.currentThread().getContextClassLoader, Array.empty)
      ThreadUtils.withThisAsContextClassLoader[Unit](flinkLoaderSimulation)(action)
    } else {
      action
    }
  }
}

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
          ir.foreach(collector.collect)
        case Right(info) =>
          exceptionHandler.handle(info)
      }
    }
  }


  class RateMeterFunction[T](groupId: String, nodeId: String) extends RichMapFunction[T, T] {
    private var instantRateMeter : RateMeter = _

    override def open(parameters: Configuration): Unit = {
      super.open(parameters)
      instantRateMeter = InstantRateMeterWithCount.register(Map("nodeId" -> nodeId), List(groupId), new MetricUtils(getRuntimeContext))
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
