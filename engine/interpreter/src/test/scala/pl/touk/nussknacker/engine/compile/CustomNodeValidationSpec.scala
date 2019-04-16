package pl.touk.nussknacker.engine.compile

import cats.data.NonEmptyList
import cats.data.Validated.Invalid
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.{Sink, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.compile.ProcessCompilationError.ExpressionParseError
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator
import pl.touk.nussknacker.engine.{api, spel}


class CustomNodeValidationSpec extends FunSuite with Matchers {
  import spel.Implicits._

  class MyProcessConfigCreator extends EmptyProcessConfigCreator {
    override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = Map(
      "myCustomStreamTransformer" -> WithCategories(SimpleStreamTransformer))
    override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = Map(
      "mySource" -> WithCategories(SimpleStringSource))
    override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map(
      "dummySink" -> WithCategories(SinkFactory.noParam(new Sink { override def testDataOutput = None })))
  }

  object SimpleStringSource extends SourceFactory[String] {
    override def clazz: Class[_] = classOf[String]
    @MethodToInvoke
    def create(): api.process.Source[String] = null
  }

  object SimpleStreamTransformer extends CustomStreamTransformer {
    @MethodToInvoke
    def execute(@ParamName("stringVal")
                @AdditionalVariables(value = Array(new AdditionalVariable(name = "additionalVar1", clazz = classOf[String])))
                stringVal: String) = {}
  }

  val processBase = EspProcessBuilder.id("proc1").exceptionHandler().source("sourceId", "mySource")
  val objectWithMethodDef = ProcessDefinitionExtractor.extractObjectWithMethods(new MyProcessConfigCreator, ConfigFactory.empty)
  val validator = ProcessValidator.default(objectWithMethodDef)

  test("valid process") {
    val validProcess = processBase
      .customNode("custom1", "outPutVar", "myCustomStreamTransformer", "stringVal" -> "#additionalVar1")
      .sink("out", "''", "dummySink")

    validator.validate(validProcess).result.isValid shouldBe true
  }

  test("invalid process with non-existing variable") {
    val invalidProcess = processBase
      .customNode("custom1", "outPutVar", "myCustomStreamTransformer", "stringVal" -> "#nonExisitngVar")
      .sink("out", "''", "dummySink")

    validator.validate(invalidProcess).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Unresolved reference nonExisitngVar", "custom1",Some("stringVal"), "#nonExisitngVar"), _))  =>
    }
  }

  test("invalid process with variable of a incorrect type") {
    val invalidProcess = processBase
      .customNode("custom1", "outPutVar", "myCustomStreamTransformer", "stringVal" -> "42")
      .sink("out", "''", "dummySink")

    validator.validate(invalidProcess).result should matchPattern {
      case Invalid(NonEmptyList(ExpressionParseError("Bad expression type, expected: type 'java.lang.String', found: type 'java.lang.Integer'", "custom1",Some("stringVal"), "42"), _))  =>
    }
  }
}
