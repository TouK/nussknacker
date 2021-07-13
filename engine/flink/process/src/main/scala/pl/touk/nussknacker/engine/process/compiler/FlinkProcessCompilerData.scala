package pl.touk.nussknacker.engine.process.compiler

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.{AsyncExecutionContextPreparer, RunMode}
import pl.touk.nussknacker.engine.api.{JobData, MetaData}
import pl.touk.nussknacker.engine.compile.ProcessCompilerData
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.Node
import pl.touk.nussknacker.engine.definition.LazyInterpreterDependencies
import pl.touk.nussknacker.engine.flink.api.RuntimeContextLifecycle
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler
import pl.touk.nussknacker.engine.flink.api.process.FlinkProcessSignalSenderProvider
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode

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
                               runMode: RunMode
                             ) {

  def open(runtimeContext: RuntimeContext, nodesToUse: List[_<:NodeData]) : Unit = {
    val lifecycle = compiledProcess.lifecycle(nodesToUse)
    lifecycle.foreach {_.open(jobData)}
    lifecycle.collect{
      case s:RuntimeContextLifecycle =>
        s.open(runtimeContext)
    }
  }

  def close(nodesToUse: List[_<:NodeData]) : Unit = {
    compiledProcess.lifecycle(nodesToUse).foreach(_.close())
  }

  def compileSubPart(node: SplittedNode[_], validationContext: ValidationContext): Node = {
    validateOrFail(compiledProcess.subPartCompiler.compile(node, validationContext)(compiledProcess.metaData, runMode).result)
  }

  private def validateOrFail[T](validated: ValidatedNel[ProcessCompilationError, T]): T = validated match {
    case Valid(r) => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  val metaData: MetaData = compiledProcess.metaData

  val interpreter : Interpreter = compiledProcess.interpreter

  val lazyInterpreterDeps: LazyInterpreterDependencies = compiledProcess.lazyInterpreterDeps

  def compileProcess(): CompiledProcessParts = validateOrFail(compiledProcess.compile()(runMode))

  def restartStrategy: RestartStrategies.RestartStrategyConfiguration = exceptionHandler.restartStrategy

  def prepareExceptionHandler(runtimeContext: RuntimeContext): FlinkEspExceptionHandler = {
    exceptionHandler.open(jobData)
    exceptionHandler.open(runtimeContext)
    exceptionHandler
  }
}


