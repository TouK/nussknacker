package pl.touk.nussknacker.engine.process.compiler

import cats.data._
import cats.data.Validated.{Invalid, Valid}
import org.apache.flink.api.common.functions.RuntimeContext
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.{Interpreter, RuntimeMode, ScenarioCompilationDependencies}
import pl.touk.nussknacker.engine.api.JobData
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ScenarioCompilationErrors, ValidationContext}
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.process.AsyncExecutionContextPreparer
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessCompilerData
import pl.touk.nussknacker.engine.compile.nodecompilation.{
  EvaluableLazyParameterCreatorDeps,
  SingleInputNodeInputValidationContext
}
import pl.touk.nussknacker.engine.compiledgraph.CompiledProcessParts
import pl.touk.nussknacker.engine.compiledgraph.node.Node
import pl.touk.nussknacker.engine.flink.api.{FlinkEngineContext, RuntimeCtx}
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
    val runtimeMode: RuntimeMode,
) {

  def open(runtimeContext: RuntimeContext, nodesToUse: List[_ <: NodeData]): Unit = {
    val lifecycle = compilerData.lifecycle(nodesToUse)
    lifecycle.foreach {
      _.open(FlinkEngineRuntimeContextImpl(jobData, RuntimeCtx(runtimeContext), runtimeMode))
    }
  }

  def close(nodesToUse: List[_ <: NodeData]): Unit = {
    compilerData.lifecycle(nodesToUse).foreach(_.close())
  }

  def compileSubPart(
      node: SplittedNode[_],
      validationContext: ValidationContext,
      engineCompilationDeps: EngineScenarioCompilationDependencies
  ): Node = {
    validateOrFail(
      compilerData.subPartCompiler
        .compile(node, SingleInputNodeInputValidationContext(validationContext))(
          new ScenarioCompilationDependencies(jobData, engineCompilationDeps)
        )
        .result
    )
  }

  private def validateOrFail[T](validated: ValidatedNel[ProcessCompilationError, T]): T = validated match {
    case Valid(r)        => r
    case Invalid(errors) => throw ScenarioCompilationErrors(errors.toList)
  }

  def jobData: JobData = compilerData.jobData

  def interpreter: Interpreter = compilerData.interpreter

  def lazyParameterDeps: EvaluableLazyParameterCreatorDeps = new EvaluableLazyParameterCreatorDeps(
    compilerData.expressionCompiler,
    compilerData.expressionEvaluator,
    jobData
  )

  def compileProcess(
      process: CanonicalProcess
  )(
      implicit engineScenarioCompilationDependencies: EngineScenarioCompilationDependencies
  ): ValidatedNel[ProcessCompilationError, CompiledProcessParts] = compilerData.compile(process)

  def compileProcessOrFail(
      process: CanonicalProcess
  )(
      implicit engineScenarioCompilationDependencies: EngineScenarioCompilationDependencies
  ): CompiledProcessParts = validateOrFail(compileProcess(process))

  def restartStrategyConfig: Configuration = exceptionHandler.restartStrategyConfig

  def prepareExceptionHandler(flinkEngineContext: FlinkEngineContext): FlinkExceptionHandler = {
    exceptionHandler.open(
      FlinkEngineRuntimeContextImpl(jobData, flinkEngineContext, runtimeMode)
    )
    exceptionHandler
  }

}
