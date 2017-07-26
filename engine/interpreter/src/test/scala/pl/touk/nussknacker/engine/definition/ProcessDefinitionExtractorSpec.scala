package pl.touk.nussknacker.engine.definition

import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.{EspExceptionHandler, EspExceptionInfo, ExceptionHandlerFactory}
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.api.signal.{ProcessSignalSender, SignalTransformer}

class ProcessDefinitionExtractorSpec extends FlatSpec with Matchers {

  it should "extract definitions" in {

    val extracted = ProcessDefinitionExtractor.extractObjectWithMethods(TestCreator, ConfigFactory.load())

    val signal1 = extracted.signalsWithTransformers.get("signal1")
    signal1 shouldBe 'defined
    signal1.get._2 shouldBe Set("transformer1")
    signal1.get._1.methodDef.method.getName shouldBe "send1"

  }

  object TestCreator extends ProcessConfigCreator {
    override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] =
      Map("transformer1" -> WithCategories(Transformer1, "cat"))

    override def services(config: Config): Map[String, WithCategories[Service]] = Map()

    override def sourceFactories(config: Config): Map[String, WithCategories[SourceFactory[_]]] = Map()

    override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map()

    override def listeners(config: Config): Seq[ProcessListener] = List()

    override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory =
      ExceptionHandlerFactory.noParams((a) => new EspExceptionHandler {
        override def handle(exceptionInfo: EspExceptionInfo[_ <: Throwable]): Unit = {}
      })

    override def globalProcessVariables(config: Config): Map[String, WithCategories[AnyRef]] = Map()

    override def buildInfo(): Map[String, String] = Map()

    override def signals(config: Config): Map[String, WithCategories[ProcessSignalSender]] = Map(
      "signal1" -> WithCategories(new Signal1, "cat")
    )
  }

  object Transformer1 extends CustomStreamTransformer {

    @MethodToInvoke
    @SignalTransformer(signalClass = classOf[Signal1])
    def invoke(@ParamName("param1") param1: String) : Unit = {}

  }

  class Signal1 extends ProcessSignalSender {
    @MethodToInvoke
    def send1(@ParamName("param1") param1: String) : Unit = {}
  }

}
