package pl.touk.nussknacker.engine.process.runner

import java.net.ConnectException

import com.typesafe.config.Config
import io.circe.Encoder
import org.scalatest.{FlatSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.flink.util.exception.BrieflyLoggingExceptionHandler
import pl.touk.nussknacker.engine.kafka.KafkaConfig
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.testing.EmptyProcessConfigCreator

class FlinkStreamingProcessMainSpec extends FlatSpec with Matchers with Inside {

  import spel.Implicits._

  it should "be able to compile and serialize services" in {
    val process =
      EspProcessBuilder
        .id("proc1")
        .exceptionHandler()
        .source("id", "input")
        .filter("filter1", "#sum(#input.![value1]) > 24")
        .processor("proc2", "logService", "all" -> "#distinct(#input.![value2])")
        .emptySink("out", "monitor")

    FlinkTestConfiguration.setQueryableStatePortRangesBySystemProperties()
    FlinkStreamingProcessMain.main(Array(ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2, Encoder[ProcessVersion].apply(ProcessVersion.empty).noSpaces))
  }

}


class SimpleProcessConfigCreator extends EmptyProcessConfigCreator {

  override def services(config: Config): Map[String, WithCategories[Service]] = Map(
    "logService" -> WithCategories(LogService, "c1"),
    "throwingService" -> WithCategories(new ThrowingService(new RuntimeException("Thrown as expected")), "c1"),
    "throwingTransientService" -> WithCategories(new ThrowingService(new ConnectException()), "c1"),
    "returningDependentTypeService" -> WithCategories(ReturningDependentTypeService, "c1")

  )

  override def sinkFactories(config: Config): Map[String, WithCategories[SinkFactory]] = Map(
    "monitor" -> WithCategories(SinkFactory.noParam(MonitorEmptySink), "c2"),
    "sinkForInts" -> WithCategories(SinkFactory.noParam(SinkForInts))
  )

  override def customStreamTransformers(config: Config): Map[String, WithCategories[CustomStreamTransformer]] = Map("stateCustom" -> WithCategories(StateCustomNode),
    "signalReader" -> WithCategories(CustomSignalReader),
    "transformWithTime" -> WithCategories(TransformerWithTime)
  )

  override def sourceFactories(config: Config): Map[String, WithCategories[FlinkSourceFactory[_]]] = Map(
    "input" -> WithCategories(simpleRecordSource(Nil), "cat2"),
    "jsonInput" -> WithCategories(jsonSource, "cat2"),
    "typedJsonInput" -> WithCategories(TypedJsonSource, "cat2")
  )

  override def signals(config: Config): Map[String, WithCategories[TestProcessSignalFactory]] = Map("sig1" ->
    WithCategories(new TestProcessSignalFactory(KafkaConfig("", None, None), "")))


  override def exceptionHandlerFactory(config: Config): ExceptionHandlerFactory =
    ExceptionHandlerFactory.noParams(BrieflyLoggingExceptionHandler(_))

}

