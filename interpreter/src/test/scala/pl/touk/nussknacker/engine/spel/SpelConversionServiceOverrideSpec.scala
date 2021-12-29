package pl.touk.nussknacker.engine.spel

import cats.data.Validated.Invalid
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers, OptionValues}
import org.springframework.core.convert.ConversionService
import org.springframework.core.convert.support.DefaultConversionService
import pl.touk.nussknacker.engine.Interpreter.IOShape
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.async.DefaultAsyncInterpretationValueDeterminer
import pl.touk.nussknacker.engine.api.exception.EspExceptionInfo
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.compile.ProcessCompilerData
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.spel.internal.SpelConversionsProvider
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import java.text.ParseException
import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class SpelConversionServiceOverrideSpec extends FunSuite with Matchers with OptionValues {

  private implicit class ValidatedValue[E, A](validated: ValidatedNel[E, A]) {
    def value: A = validated.valueOr(err => throw new ParseException(err.toList.mkString, -1))
  }

  class SomeService extends Service {
    @MethodToInvoke
    def invoke(@ParamName("listParam") param: java.util.List[Any]): Future[Any] = Future.successful(param)
  }

  class MyProcessConfigCreator(spelCustomConversionsProviderOpt: Option[SpelConversionsProvider]) extends EmptyProcessConfigCreator {
    override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] =
      Map("stringSource" -> WithCategories(SourceFactory.noParam[String](new  pl.touk.nussknacker.engine.api.process.Source {})))


    override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = {
      Map("service" -> WithCategories(new SomeService))
    }

    override def expressionConfig(processObjectDependencies: ProcessObjectDependencies): ExpressionConfig = {
      ExpressionConfig(
        globalProcessVariables = Map("CONV" -> WithCategories(ConversionUtils)),
        globalImports = List.empty,
        customConversionsProviders = spelCustomConversionsProviderOpt.toList)
    }
  }

  test("be able to override Nussknacker default spel conversion service") {
    val process = EspProcessBuilder
      .id("test")
      .exceptionHandlerNoParams()
      .source("start", "stringSource")
      // here is done conversion from comma separated string to list[string] which is currently not supported by nussknacker typing
      // system so is also disabled in spel evaluation but can be tunred on by passing customConversionsProviders with SpEL's DefaultConversionService
      .processorEnd("invoke-service", "service", "listParam" -> "#CONV.toAny(#input)")
    val inputValue = "123,234"

    interpret(process, None, inputValue) should matchPattern {
      case Invalid(NonEmptyList(EspExceptionInfo(Some("invoke-service"), ex, _), Nil)) if ex.getMessage.contains("cannot convert from java.lang.String to java.util.List<?>") =>
    }

    val defaultSpelConversionServiceProvider = new SpelConversionsProvider {
      override def getConversionService: ConversionService =
        new DefaultConversionService
    }
    val outputValue = interpret(process, Some(defaultSpelConversionServiceProvider), inputValue).value.output
    outputValue shouldEqual List("123", "234").asJava
  }

  private def interpret(process: EspProcess, spelCustomConversionsProviderOpt: Option[SpelConversionsProvider], inputValue: Any) = {
    val modelData = LocalModelData(ConfigFactory.empty(), new MyProcessConfigCreator(spelCustomConversionsProviderOpt))
    val compilerData = ProcessCompilerData.prepare(process, modelData.processWithObjectsDefinition, Seq.empty, getClass.getClassLoader,
      ProductionServiceInvocationCollector, RunMode.Normal)(DefaultAsyncInterpretationValueDeterminer.DefaultValue)
    val parts = compilerData.compile().value
    val source = parts.sources.head
    val compiledNode = compilerData.subPartCompiler.compile(source.node, source.validationContext)(process.metaData).result.value

    val inputContext = Context("foo").withVariable(VariableConstants.InputVariableName, inputValue)
    Validated.fromEither(compilerData.interpreter.interpret(compiledNode, parts.metaData, inputContext).unsafeRunSync().head.swap).toValidatedNel
  }

  object ConversionUtils extends HideToString {
    def toAny(@ParamName("value") value: Any): Any = {
      value
    }
  }

}
