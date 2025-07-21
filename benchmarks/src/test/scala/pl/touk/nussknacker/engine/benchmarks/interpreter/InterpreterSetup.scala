package pl.touk.nussknacker.engine.benchmarks.interpreter

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import pl.touk.nussknacker.engine.{
  api,
  CustomProcessValidatorLoader,
  InterpretationResult,
  RuntimeMode,
  ScenarioCompilationDependencies
}
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.ModelConfig.GlobalParametersConfig
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  DesignerWideComponentId,
  NodesDeploymentData,
  UnboundedStreamComponent
}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.definition.EngineScenarioCompilationDependencies
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessCompilerData
import pl.touk.nussknacker.engine.compile.nodecompilation.SingleInputNodeInputValidationContext
import pl.touk.nussknacker.engine.compiledgraph.part.ProcessPart
import pl.touk.nussknacker.engine.definition.component.Components
import pl.touk.nussknacker.engine.definition.component.Components.ComponentDefinitionExtractionMode
import pl.touk.nussknacker.engine.definition.model.{ModelDefinition, ModelDefinitionWithClasses}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder

import scala.language.higherKinds
import scala.reflect.ClassTag

class InterpreterSetup[T: ClassTag] {

  def sourceInterpretation[F[_]: Monad: InterpreterShape](
      process: CanonicalProcess,
      additionalComponents: List[ComponentDefinition]
  ): (Context, ServiceExecutionContext) => F[List[Either[InterpretationResult, NuExceptionInfo]]] = {
    val jobData      = JobData(process.metaData, ProcessVersion.empty.copy(processName = process.metaData.name))
    val compilerData = prepareCompilerData(jobData, additionalComponents)
    val interpreter  = compilerData.interpreter

    implicit val engineScenarioCompilationDependencies: EngineScenarioCompilationDependencies =
      EngineScenarioCompilationDependencies.empty
    implicit val scenarioCompilationDependencies: ScenarioCompilationDependencies =
      new ScenarioCompilationDependencies(jobData, engineScenarioCompilationDependencies)

    val parts = failOnErrors(compilerData.compile(process))

    def compileNode(part: ProcessPart) =
      failOnErrors(
        compilerData.subPartCompiler
          .compile(part.node, SingleInputNodeInputValidationContext(part.validationContext))
          .result
      )

    val compiled = compileNode(parts.sources.head)
    (initialCtx: Context, ec: ServiceExecutionContext) => interpreter.interpret[F](compiled, jobData, initialCtx, ec)
  }

  def prepareCompilerData(
      jobData: JobData,
      additionalComponents: List[ComponentDefinition],
  ): ProcessCompilerData = {
    val components = List(
      ComponentDefinition("source", new Source),
      ComponentDefinition("sink", SinkFactory.noParam(new Sink {}))
    ) ::: additionalComponents

    val globalParametersConfig = GlobalParametersConfig.default

    val definitions = ModelDefinition(
      Components
        .forList(
          components,
          ComponentsUiConfig.Empty,
          id => DesignerWideComponentId(id.toString),
          Map.empty,
          globalParametersConfig,
          ComponentDefinitionExtractionMode.FinalDefinition
        ),
      ModelDefinitionBuilder.emptyExpressionConfig,
      ClassExtractionSettings.Default,
      allowEndingScenarioWithoutSink = false,
      globalParametersConfig
    )
    val definitionsWithTypes = ModelDefinitionWithClasses(definitions)

    ProcessCompilerData.prepare(
      jobData,
      definitionsWithTypes,
      new SimpleDictRegistry(Map.empty).toEngineRegistry,
      List.empty,
      getClass.getClassLoader,
      ProductionServiceInvocationCollector,
      RuntimeMode.Live,
      CustomProcessValidatorLoader.emptyCustomProcessValidator,
      NodesDeploymentData.empty,
    )
  }

  private def failOnErrors[Y](obj: ValidatedNel[ProcessCompilationError, Y]): Y = obj match {
    case Valid(c)     => c
    case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  class Source extends SourceFactory with UnboundedStreamComponent {

    @MethodToInvoke
    def create(): api.process.Source = null

  }

}
