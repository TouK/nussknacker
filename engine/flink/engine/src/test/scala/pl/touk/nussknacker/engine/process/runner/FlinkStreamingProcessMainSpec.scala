package pl.touk.nussknacker.engine.process.runner

import io.circe.Encoder
import org.scalatest.{FlatSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.exception.ExceptionHandlerFactory
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.flink.util.exception.ConfigurableExceptionHandlerFactory
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.util.process.EmptyProcessConfigCreator

import java.net.ConnectException

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
    FlinkStreamingProcessMain.main(Array(ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2,
      Encoder[ProcessVersion].apply(ProcessVersion.empty).noSpaces, Encoder[DeploymentData].apply(DeploymentData.empty).noSpaces))
  }

}


class SimpleProcessConfigCreator extends EmptyProcessConfigCreator {

  override def services(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] = Map(
    "logService" -> WithCategories(LogService, "c1"),
    "throwingService" -> WithCategories(new ThrowingService(new RuntimeException("Thrown as expected")), "c1"),
    "throwingTransientService" -> WithCategories(new ThrowingService(new ConnectException()), "c1"),
    "returningDependentTypeService" -> WithCategories(ReturningDependentTypeService, "c1"),
    "collectingEager" -> WithCategories(CollectingEagerService, "c1"),
    "returningRunModeService" -> WithCategories(ReturningRunModeService, "c1")
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    "monitor" -> WithCategories(SinkFactory.noParam(MonitorEmptySink), "c2"),
    "valueMonitor" -> WithCategories(SinkForAny.toSinkFactory, "c2"),
    "sinkForInts" -> WithCategories(SinkForInts.toSinkFactory)
  )

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map("stateCustom" -> WithCategories(StateCustomNode),
    "transformWithTime" -> WithCategories(TransformerWithTime),
    "joinBranchExpression" -> WithCategories(CustomJoinUsingBranchExpressions),
    "transformerAddingRunMode" -> WithCategories(TransformerAddingRunMode)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map(
    "input" -> WithCategories(simpleRecordSource(Nil), "cat2"),
    "jsonInput" -> WithCategories(jsonSource, "cat2"),
    "typedJsonInput" -> WithCategories(TypedJsonSource, "cat2"),
    "genericSourceWithCustomVariables" -> WithCategories(GenericSourceWithCustomVariables)
  )

  override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[ProcessSignalSender]] =
    Map.empty

  override def exceptionHandlerFactory(processObjectDependencies: ProcessObjectDependencies): ExceptionHandlerFactory =
    ConfigurableExceptionHandlerFactory(processObjectDependencies)

}

