package pl.touk.nussknacker.engine.process.compiler

import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, _}
import com.codahale.metrics.{Histogram, SlidingTimeWindowReservoir}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.dropwizard.metrics.DropwizardHistogramWrapper
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.{AsyncExecutionContextPreparer, RunMode}
import pl.touk.nussknacker.engine.api.{JobData, MetaData}
import pl.touk.nussknacker.engine.baseengine.api.metrics.MetricsProvider
import pl.touk.nussknacker.engine.baseengine.api.runtimecontext.{EngineRuntimeContext, RuntimeContextLifecycle}
import pl.touk.nussknacker.engine.compile.ProcessCompilerData
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.Node
import pl.touk.nussknacker.engine.definition.LazyInterpreterDependencies
import pl.touk.nussknacker.engine.flink.api
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process.FlinkProcessSignalSenderProvider
import pl.touk.nussknacker.engine.flink.util.metrics.{InstantRateMeter, MetricUtils}
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode
import pl.touk.nussknacker.engine.util.service.EspTimer

import java.util.concurrent.TimeUnit
import scala.concurrent.duration.FiniteDuration

/*
  This class augments ProcessCompilerData with Flink specific stuff. In particular, we handle Flink lifecycle here.
  Instances are created inside Flink operators (they use compileSubPart)
  and one additional instance is created during graph creation (it uses compileProcess).

  NOTE: this class is *NOT* serializable, it should be created on each operator via FlinkProcessCompiler.
 */
class FlinkProcessCompilerData(compiledProcess: ProcessCompilerData,
                               val jobData: JobData,
                               // Exception handler is not opened and closed in this class. Use prepareExceptionHandler.
                               exceptionHandler: FlinkEspExceptionHandler,
                               val signalSenders: FlinkProcessSignalSenderProvider,
                               val asyncExecutionContextPreparer: AsyncExecutionContextPreparer,
                               val processTimeout: FiniteDuration,
                               val runMode: RunMode
                              ) {

  def open(runtimeContext: RuntimeContext, nodesToUse: List[_ <: NodeData]): Unit = {
    val lifecycle = compiledProcess.lifecycle(nodesToUse)
    lifecycle.foreach {
      _.open(jobData)
    }
    lifecycle.collect {
      case s: api.RuntimeContextLifecycle => s.open(runtimeContext)
      case s: RuntimeContextLifecycle =>
        s.open(jobData, EngineRuntimeContext(jobData.metaData.id,
          //todo extract
          new MetricsProvider {
            val metricUtils = new MetricUtils(runtimeContext)

            override def espTimer(processId: String, instantTimerWindowInSeconds: Long, tags: Map[String, String], name: NonEmptyList[String]): EspTimer = {
              val meter = metricUtils.gauge[Double, InstantRateMeter](name :+ EspTimer.instantRateSuffix, tags, new InstantRateMeter)
              val histogram = new DropwizardHistogramWrapper(new Histogram(new SlidingTimeWindowReservoir(instantTimerWindowInSeconds, TimeUnit.SECONDS)))
              val registered = metricUtils.histogram(name :+ EspTimer.histogramSuffix, tags, histogram)
              EspTimer(meter, registered.update)
            }

            override def close(processId: String): Unit = {}
          }))
    }
  }

  def close(nodesToUse: List[_ <: NodeData]): Unit = {
    compiledProcess.lifecycle(nodesToUse).foreach(_.close())
  }

  def compileSubPart(node: SplittedNode[_], validationContext: ValidationContext): Node = {
    validateOrFail(compiledProcess.subPartCompiler.compile(node, validationContext)(compiledProcess.metaData).result)
  }

  private def validateOrFail[T](validated: ValidatedNel[ProcessCompilationError, T]): T = validated match {
    case Valid(r) => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  val metaData: MetaData = compiledProcess.metaData

  val interpreter: Interpreter = compiledProcess.interpreter

  val lazyInterpreterDeps: LazyInterpreterDependencies = compiledProcess.lazyInterpreterDeps

  def compileProcess(): CompiledProcessParts = validateOrFail(compiledProcess.compile())

  def restartStrategy: RestartStrategies.RestartStrategyConfiguration = exceptionHandler.restartStrategy

  def prepareExceptionHandler(runtimeContext: RuntimeContext): FlinkEspExceptionHandler = {
    exceptionHandler.open(jobData)
    exceptionHandler.open(runtimeContext)
    exceptionHandler
  }
}


