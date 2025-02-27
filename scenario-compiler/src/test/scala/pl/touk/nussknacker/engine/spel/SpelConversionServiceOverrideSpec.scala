package pl.touk.nussknacker.engine.spel

import cats.data.{NonEmptyList, Validated, ValidatedNel}
import cats.data.Validated.Invalid
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.typesafe.config.ConfigFactory
import org.scalatest.OptionValues
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.support.DefaultConversionService
import pl.touk.nussknacker.engine.{ComponentUseCase, CustomProcessValidatorLoader}
import pl.touk.nussknacker.engine.Interpreter.IOShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.{
  ComponentDefinition,
  ComponentId,
  ComponentType,
  NodeComponentInfo,
  NodesDeploymentData
}
import pl.touk.nussknacker.engine.api.exception.NuExceptionInfo
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.spel.SpelConversionsProvider
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.ProcessCompilerData
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.SynchronousExecutionContextAndIORuntime

import java.text.ParseException
import scala.concurrent.Future
import scala.jdk.CollectionConverters._

class SpelConversionServiceOverrideSpec extends AnyFunSuite with Matchers with OptionValues {

  private implicit class ValidatedValue[E, A](validated: ValidatedNel[E, A]) {
    def value: A = validated.valueOr(err => throw new ParseException(err.toList.mkString, -1))
  }

  class SomeService extends Service {
    @MethodToInvoke
    def invoke(@ParamName("listParam") param: java.util.List[Any]): Future[Any] = Future.successful(param)
  }

  private val components = List(
    ComponentDefinition(
      "stringSource",
      SourceFactory.noParamUnboundedStreamFactory[String](new pl.touk.nussknacker.engine.api.process.Source {})
    ),
    ComponentDefinition("service", new SomeService),
  )

  class WithConvUtilConfigCreator(spelCustomConversionsProviderOpt: Option[SpelConversionsProvider])
      extends EmptyProcessConfigCreator {

    override def expressionConfig(modelDependencies: ProcessObjectDependencies): ExpressionConfig = {
      ExpressionConfig(
        globalProcessVariables = Map("CONV" -> WithCategories.anyCategory(ConversionUtils)),
        globalImports = List.empty,
        customConversionsProviders = spelCustomConversionsProviderOpt.toList
      )
    }

  }

  test("be able to override Nussknacker default spel conversion service") {
    val process = ScenarioBuilder
      .streaming("test")
      .source("start", "stringSource")
      // here is done conversion from comma separated string to list[string] which is currently not supported by nussknacker typing
      // system so is also disabled in spel evaluation but can be turned on by passing customConversionsProviders with SpEL's DefaultConversionService
      .enricher("invoke-service", "output", "service", "listParam" -> "#CONV.toAny(#input)".spel)
      .processorEnd("dummy", "service", "listParam" -> "{}".spel)
    val inputValue = "123,234"

    interpret(process, None, inputValue) should matchPattern {
      case Invalid(
            NonEmptyList(
              NuExceptionInfo(
                Some(NodeComponentInfo("invoke-service", Some(ComponentId(ComponentType.Service, "service")))),
                ex,
                _
              ),
              Nil
            )
          ) if ex.getMessage.contains("cannot convert from java.lang.String to java.util.List<?>") =>
    }

    val defaultSpelConversionServiceProvider = new SpelConversionsProvider {
      override def getConversionService: ConversionService =
        new DefaultConversionService
    }
    val outputValue =
      interpret(process, Some(defaultSpelConversionServiceProvider), inputValue).value.finalContext[AnyRef]("output")
    outputValue shouldEqual List("123", "234").asJava
  }

  private def interpret(
      process: CanonicalProcess,
      spelCustomConversionsProviderOpt: Option[SpelConversionsProvider],
      inputValue: Any
  ) = {
    val modelData =
      LocalModelData(
        ConfigFactory.empty(),
        components,
        configCreator = new WithConvUtilConfigCreator(spelCustomConversionsProviderOpt)
      )
    val jobData: JobData =
      JobData(process.metaData, ProcessVersion.empty.copy(processName = process.metaData.name))
    val compilerData = ProcessCompilerData.prepare(
      jobData,
      modelData.modelDefinitionWithClasses,
      modelData.engineDictRegistry,
      Seq.empty,
      getClass.getClassLoader,
      ProductionServiceInvocationCollector,
      ComponentUseCase.EngineRuntime,
      CustomProcessValidatorLoader.emptyCustomProcessValidator,
      NodesDeploymentData.empty,
    )
    val parts  = compilerData.compile(process).value
    val source = parts.sources.head
    val compiledNode =
      compilerData.subPartCompiler.compile(source.node, source.validationContext)(jobData).result.value

    val inputContext                = Context("foo").withVariable(VariableConstants.InputVariableName, inputValue)
    implicit val runtime: IORuntime = cats.effect.unsafe.implicits.global
    Validated
      .fromEither(
        compilerData.interpreter
          .interpret[IO](
            compiledNode,
            jobData,
            inputContext,
            ServiceExecutionContext(SynchronousExecutionContextAndIORuntime.syncEc)
          )
          .unsafeRunSync()
          .head
          .swap
      )
      .toValidatedNel
  }

  object ConversionUtils extends HideToString {

    def toAny(@ParamName("value") value: Any): Any = {
      value
    }

  }

}
