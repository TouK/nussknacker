package pl.touk.nussknacker.engine.benchmarks.interpreter

import cats.Monad
import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.ComponentDefinition
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessCompilerData
import pl.touk.nussknacker.engine.compiledgraph.part.ProcessPart
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.definition.model.{
  ModelDefinition,
  ModelDefinitionExtractor,
  ModelDefinitionWithClasses
}
import pl.touk.nussknacker.engine.dict.SimpleDictRegistry
import pl.touk.nussknacker.engine.modelconfig.ComponentsUiConfig
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.testing.ModelDefinitionBuilder
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.{CustomProcessValidatorLoader, InterpretationResult, api}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import scala.reflect.ClassTag

class InterpreterSetup[T: ClassTag] {

  def sourceInterpretation[F[_]: Monad: InterpreterShape](
      process: CanonicalProcess,
      additionalComponents: List[ComponentDefinition]
  ): (Context, ExecutionContext) => F[
    List[Either[InterpretationResult, NuExceptionInfo[_ <: Throwable]]]
  ] = {
    val compiledProcess = compile(additionalComponents, process)
    val interpreter     = compiledProcess.interpreter
    val parts           = failOnErrors(compiledProcess.compile())

    def compileNode(part: ProcessPart) =
      failOnErrors(compiledProcess.subPartCompiler.compile(part.node, part.validationContext)(process.metaData).result)

    val compiled = compileNode(parts.sources.head)
    (initialCtx: Context, ec: ExecutionContext) =>
      interpreter.interpret[F](compiled, process.metaData, initialCtx, ServiceExecutionContext(ec))
  }

  def compile(
      additionalComponents: List[ComponentDefinition],
      process: CanonicalProcess
  ): ProcessCompilerData = {
    val components = List(
      ComponentDefinition("source", new Source),
      ComponentDefinition("sink", SinkFactory.noParam(new Sink {}))
    ) ::: additionalComponents

    val definitions = ModelDefinition(
      ComponentDefinitionWithImplementation.forList(components, ComponentsUiConfig.Empty),
      ModelDefinitionBuilder.toDefinitionWithImpl(ModelDefinitionBuilder.emptyExpressionConfig),
      ClassExtractionSettings.Default
    )
    val definitionsWithTypes = ModelDefinitionWithClasses(definitions)

    ProcessCompilerData.prepare(
      process,
      ConfigFactory.empty(),
      definitionsWithTypes,
      new SimpleDictRegistry(Map.empty).toEngineRegistry,
      List.empty,
      getClass.getClassLoader,
      ProductionServiceInvocationCollector,
      ComponentUseCase.EngineRuntime,
      CustomProcessValidatorLoader.emptyCustomProcessValidator
    )
  }

  private def failOnErrors[Y](obj: ValidatedNel[ProcessCompilationError, Y]): Y = obj match {
    case Valid(c)     => c
    case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  class Source extends SourceFactory {

    @MethodToInvoke
    def create(): api.process.Source = null

  }

}
