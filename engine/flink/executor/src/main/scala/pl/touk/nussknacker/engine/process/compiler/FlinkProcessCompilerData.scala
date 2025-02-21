package pl.touk.nussknacker.engine.process.compiler

import cats.data.Validated.{Invalid, Valid}
import cats.data._
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.Interpreter
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.process.{AsyncExecutionContextPreparer, ComponentUseCase}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessCompilerData
import pl.touk.nussknacker.engine.compile.nodecompilation.EvaluableLazyParameterCreatorDeps
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.Node
import pl.touk.nussknacker.engine.graph.node.NodeData
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.splittedgraph.splittednode.SplittedNode

import scala.concurrent.duration.FiniteDuration

/*
  This class augments ProcessCompilerData with Flink specific stuff. In particular, we handle Flink lifecycle here.
  Instances are created inside Flink operators (they use compileSubPart)
  and one additional instance is created during graph creation (it uses compileProcess).

  NOTE: this class is *NOT* serializable, it should be created on each operator via FlinkProcessCompiler.
 */
class FlinkProcessCompilerData(
    compilerData: ProcessCompilerData,
    // Exception handler is not opened and closed in this class. Use prepareExceptionHandler.
    exceptionHandler: FlinkExceptionHandler,
    val asyncExecutionContextPreparer: AsyncExecutionContextPreparer,
    val processTimeout: FiniteDuration,
    val componentUseCase: ComponentUseCase
) {

  def open(runtimeContext: RuntimeContext, nodesToUse: List[_ <: NodeData]): Unit = {
    val lifecycle = compilerData.lifecycle(nodesToUse)
    lifecycle.foreach {
      _.open(FlinkEngineRuntimeContextImpl(jobData, runtimeContext, componentUseCase))
    }
  }

  def close(nodesToUse: List[_ <: NodeData]): Unit = {
    compilerData.lifecycle(nodesToUse).foreach(_.close())
  }

  def compileSubPart(node: SplittedNode[_], validationContext: ValidationContext): Node = {
    validateOrFail(compilerData.subPartCompiler.compile(node, validationContext)(jobData).result)
  }

  private def validateOrFail[T](validated: ValidatedNel[ProcessCompilationError, T]): T = validated match {
    case Valid(r)     => r
    case Invalid(err) => throw new scala.IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  def jobData: JobData = compilerData.jobData

  def interpreter: Interpreter = compilerData.interpreter

  def lazyParameterDeps: EvaluableLazyParameterCreatorDeps = new EvaluableLazyParameterCreatorDeps(
    compilerData.expressionCompiler,
    compilerData.expressionEvaluator,
    jobData
  )

  def compileProcess(process: CanonicalProcess): ValidatedNel[ProcessCompilationError, CompiledProcessParts] =
    compilerData.compile(process)

  def compileProcessOrFail(process: CanonicalProcess): CompiledProcessParts = validateOrFail(compileProcess(process))

  def restartStrategy: RestartStrategies.RestartStrategyConfiguration = exceptionHandler.restartStrategy

  def prepareExceptionHandler(runtimeContext: RuntimeContext): FlinkExceptionHandler = {
    exceptionHandler.open(
      FlinkEngineRuntimeContextImpl(jobData, runtimeContext, componentUseCase)
    )
    exceptionHandler
  }

}
