package pl.touk.nussknacker.engine.process.runner

import io.circe.Encoder
import io.circe.syntax._
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, _}
import pl.touk.nussknacker.engine.api.signal.ProcessSignalSender
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.spel

import java.net.ConnectException

class FlinkStreamingProcessMainSpec extends AnyFlatSpec with Matchers with Inside {

  import spel.Implicits._

  it should "be able to compile and serialize services" in {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .filter("filter1", "#sum(#input.![value1]) > 24")
        .processor("proc2", "logService", "all" -> "#distinct(#input.![value2])")
        .emptySink("out", "monitor")

    FlinkTestConfiguration.setQueryableStatePortRangesBySystemProperties()
    FlinkStreamingProcessMain.main(Array(process.toCanonicalProcess.asJson.spaces2,
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
    "returningComponentUseCaseService" -> WithCategories(ReturningComponentUseCaseService, "c1")
  )

  override def sinkFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SinkFactory]] = Map(
    "monitor" -> WithCategories(SinkFactory.noParam(MonitorEmptySink), "c2"),
    "valueMonitor" -> WithCategories(SinkForAny.toSinkFactory, "c2"),
    "sinkForInts" -> WithCategories(SinkForInts.toSinkFactory)
  )

  override def customStreamTransformers(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[CustomStreamTransformer]] = Map("stateCustom" -> WithCategories(StateCustomNode),
    "transformWithTime" -> WithCategories(TransformerWithTime),
    "joinBranchExpression" -> WithCategories(CustomJoinUsingBranchExpressions),
    "transformerAddingComponentUseCase" -> WithCategories(TransformerAddingComponentUseCase)
  )

  override def sourceFactories(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[SourceFactory]] = Map(
    "input" -> WithCategories(simpleRecordSource(Nil), "cat2"),
    "jsonInput" -> WithCategories(jsonSource, "cat2"),
    "typedJsonInput" -> WithCategories(TypedJsonSource, "cat2"),
    "genericSourceWithCustomVariables" -> WithCategories(GenericSourceWithCustomVariables)
  )

  override def signals(processObjectDependencies: ProcessObjectDependencies): Map[String, WithCategories[ProcessSignalSender]] =
    Map.empty
}

