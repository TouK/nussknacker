package pl.touk.nussknacker.engine.benchmarks.interpreter

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import com.typesafe.config.ConfigFactory
import pl.touk.nussknacker.engine.Interpreter.InterpreterShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessCompilerData
import pl.touk.nussknacker.engine.compiledgraph.part.ProcessPart
import pl.touk.nussknacker.engine.definition.{ProcessDefinitionExtractor, SubprocessDefinitionExtractor}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.engine.util.namespaces.ObjectNamingProvider
import pl.touk.nussknacker.engine.{CustomProcessValidatorLoader, InterpretationResult, api}

import scala.concurrent.ExecutionContext
import scala.language.higherKinds
import scala.reflect.ClassTag

class InterpreterSetup[T:ClassTag] {

  def sourceInterpretation[F[_]:InterpreterShape](process: CanonicalProcess,
                           services: Map[String, Service],
                           listeners: Seq[ProcessListener]): (Context, ExecutionContext) => F[List[Either[InterpretationResult, NuExceptionInfo[_ <: Throwable]]]] = {
    val compiledProcess = compile(services, process, listeners)
    val interpreter = compiledProcess.interpreter
    val parts = failOnErrors(compiledProcess.compile())

    def compileNode(part: ProcessPart) =
      failOnErrors(compiledProcess.subPartCompiler.compile(part.node, part.validationContext)(process.metaData).result)
    val compiled = compileNode(parts.sources.head)
    val shape = implicitly[InterpreterShape[F]]
    (initialCtx: Context, ec: ExecutionContext) =>
      interpreter.interpret[F](compiled, process.metaData, initialCtx)(shape, ec)
  }

  def compile(servicesToUse: Map[String, Service], process: CanonicalProcess, listeners: Seq[ProcessListener]): ProcessCompilerData = {

    val configCreator: ProcessConfigCreator = new EmptyProcessConfigCreator {

      override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = servicesToUse.mapValuesNow(WithCategories(_))

      override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
        Map("source" -> WithCategories(new Source))

      override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]]
      = Map("sink" -> WithCategories(SinkFactory.noParam(new Sink {})))
    }

    val definitions = ProcessDefinitionExtractor.extractObjectWithMethods(configCreator,
      api.process.ProcessObjectDependencies(ConfigFactory.empty(), ObjectNamingProvider(getClass.getClassLoader)))
    val subprocessDefinitionExtractor = SubprocessDefinitionExtractor(ConfigFactory.empty(), getClass.getClassLoader)

    ProcessCompilerData.prepare(process, definitions, subprocessDefinitionExtractor, listeners, getClass.getClassLoader, ProductionServiceInvocationCollector, ComponentUseCase.EngineRuntime, CustomProcessValidatorLoader.emptyCustomProcessValidator)
  }

  private def failOnErrors[Y](obj: ValidatedNel[ProcessCompilationError, Y]): Y = obj match {
    case Valid(c) => c
    case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Compilation errors: ", ", ", ""))
  }

  class Source extends SourceFactory {
    
    @MethodToInvoke
    def create(): api.process.Source = null

  }

}
