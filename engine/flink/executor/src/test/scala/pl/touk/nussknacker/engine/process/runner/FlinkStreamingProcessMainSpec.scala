package pl.touk.nussknacker.engine.process.runner

import io.circe.Encoder
import io.circe.syntax._
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.scalatest.Inside
import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.flink.test.FlinkTestConfiguration
import pl.touk.nussknacker.engine.process.helpers.SampleNodes._
import pl.touk.nussknacker.engine.process.helpers.TestResultsHolder
import pl.touk.nussknacker.engine.process.runner.SimpleProcessConfigCreator.{
  sinkForIntsResultsHolder,
  valueMonitorResultsHolder
}

import java.net.ConnectException

class FlinkStreamingProcessMainSpec extends AnyFlatSpec with Matchers with Inside {

  import pl.touk.nussknacker.engine.spel.SpelExtension._

  object TestFlinkStreamingProcessMain extends BaseFlinkStreamingProcessMain {

    override protected def getExecutionEnvironment: StreamExecutionEnvironment = {
      StreamExecutionEnvironment.getExecutionEnvironment(FlinkTestConfiguration.configuration())
    }

  }

  it should "be able to compile and serialize services" in {
    val process =
      ScenarioBuilder
        .streaming("proc1")
        .source("id", "input")
        .filter("filter1", "#sum(#input.![value1]) > 24".spel)
        .processor("proc2", "logService", "all" -> "#distinct(#input.![value2])".spel)
        .emptySink("out", "monitor")

    TestFlinkStreamingProcessMain.main(
      Array(
        process.asJson.spaces2,
        Encoder[ProcessVersion].apply(ProcessVersion.empty).noSpaces,
        Encoder[DeploymentData].apply(DeploymentData.empty).noSpaces
      )
    )
  }

}

class SimpleProcessConfigCreator extends EmptyProcessConfigCreator {

  override def services(modelDependencies: ProcessObjectDependencies): Map[String, WithCategories[Service]] =
    Map(
      "logService"      -> WithCategories(LogService, "c1"),
      "throwingService" -> WithCategories(new ThrowingService(new RuntimeException("Thrown as expected")), "c1"),
      "throwingTransientService"         -> WithCategories(new ThrowingService(new ConnectException()), "c1"),
      "returningDependentTypeService"    -> WithCategories(ReturningDependentTypeService, "c1"),
      "collectingEager"                  -> WithCategories(CollectingEagerService, "c1"),
      "returningComponentUseCaseService" -> WithCategories(ReturningComponentUseCaseService, "c1")
    )

  override def sinkFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SinkFactory]] = Map(
    "monitor"      -> WithCategories(SinkFactory.noParam(MonitorEmptySink), "c2"),
    "valueMonitor" -> WithCategories(SinkForAny(valueMonitorResultsHolder), "c2"),
    "sinkForInts"  -> WithCategories.anyCategory(SinkForInts(sinkForIntsResultsHolder))
  )

  override def customStreamTransformers(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[CustomStreamTransformer]] = Map(
    "stateCustom"                       -> WithCategories.anyCategory(StateCustomNode),
    "transformWithTime"                 -> WithCategories.anyCategory(TransformerWithTime),
    "joinBranchExpression"              -> WithCategories.anyCategory(CustomJoinUsingBranchExpressions),
    "transformerAddingComponentUseCase" -> WithCategories.anyCategory(TransformerAddingComponentUseCase)
  )

  override def sourceFactories(
      modelDependencies: ProcessObjectDependencies
  ): Map[String, WithCategories[SourceFactory]] = Map(
    "input"                            -> WithCategories(simpleRecordSource(Nil), "cat2"),
    "jsonInput"                        -> WithCategories(jsonSource, "cat2"),
    "typedJsonInput"                   -> WithCategories(TypedJsonSource, "cat2"),
    "genericSourceWithCustomVariables" -> WithCategories.anyCategory(GenericSourceWithCustomVariables)
  )

}

object SimpleProcessConfigCreator extends Serializable {

  val valueMonitorResultsHolder = new TestResultsHolder[AnyRef]
  val sinkForIntsResultsHolder  = new TestResultsHolder[java.lang.Integer]

}
