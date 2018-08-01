package pl.touk.nussknacker.engine.definition

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.{ProcessSignalSender, SignalTransformer}
import pl.touk.nussknacker.engine.api.typed.typing.Typed

class ProcessDefinitionExtractorSpec extends FlatSpec with Matchers {

  val processDefinition =
    ProcessDefinitionExtractor.extractObjectWithMethods(TestCreator, ConfigFactory.load())

  it should "extract definitions" in {
    val signal1 = processDefinition.signalsWithTransformers.get("signal1")
    signal1 shouldBe 'defined
    signal1.get._2 shouldBe Set("transformer1")
    signal1.get._1.methodDef.name shouldBe "send1"
  }

  it should "extract additional variables info from annotation" in {
    val methodDef = processDefinition.customStreamTransformers("transformer1")._1.methodDef
    val additionalVars = methodDef.orderedParameters.definedParameters.head.additionalVariables
    additionalVars("var1") shouldBe Typed[String]
  }

  object TestCreator extends ProcessConfigCreator {
    override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] =
      Map("transformer1" -> WithCategories(Transformer1, "cat"))

    override def services(config: Config): Map[String, WithCategories[Service]] = Map()

    override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = Map()

    override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map()

    override def listeners(config: Config): Seq[ProcessListener] = List()

    override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory =
      ExceptionHandlerFactory.noParams(_ => EspExceptionHandler.empty)

    override def expressionConfig(config: Config) = ExpressionConfig(Map.empty, List.empty)

    override def buildInfo(): Map[String, String] = Map()

    override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] = Map(
      "signal1" -> WithCategories(new Signal1, "cat")
    )
  }

  object Transformer1 extends CustomStreamTransformer {

    @MethodToInvoke
    @SignalTransformer(signalClass = classOf[Signal1])
    def invoke(
      @ParamName("param1")
      @AdditionalVariables(value = Array(new AdditionalVariable(name = "var1", clazz = classOf[String])))
      param1: String) : Unit = {}
  }

  class Signal1 extends ProcessSignalSender {
    @MethodToInvoke
    def send1(@ParamName("param1") param1: String) : Unit = {}
  }
}
