package pl.touk.nussknacker.engine.kafka.source.flink

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSource.OffsetResetStrategy
import pl.touk.nussknacker.engine.kafka.source.flink.FlinkKafkaSource.OffsetResetStrategy.OffsetResetStrategy
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryMixin.{ObjToSerialize, SampleKey, SampleValue}
import pl.touk.nussknacker.engine.kafka.source.flink.KafkaSourceFactoryProcessConfigCreator.ResultsHolders

class KafkaSourceWithDeploymentParametersSpec extends KafkaSourceFactoryProcessMixin {

  private val TestSampleValue   = SampleValue("some id", "some field")
  private val TestSampleKey     = SampleKey("some key", 123L)
  private val TestSampleHeaders = Map("first" -> "header value", "second" -> null)

  private val eventsBeforeStart = List(
    ObjToSerialize(TestSampleValue.copy(id = "a"), TestSampleKey, TestSampleHeaders),
    ObjToSerialize(TestSampleValue.copy(id = "b"), TestSampleKey, TestSampleHeaders),
    ObjToSerialize(TestSampleValue.copy(id = "c"), TestSampleKey, TestSampleHeaders),
  )

  private val eventsAfterStart = List(
    ObjToSerialize(TestSampleValue.copy(id = "d"), TestSampleKey, TestSampleHeaders)
  )

  override protected val resultHolders: () => ResultsHolders = () =>
    KafkaSourceWithDeploymentParametersSpec.resultsHolders

  test("should start reading from the beginning when restart offset") {
    val topic = "kafka-from-earliest"
    createTopic(topic)

    createEvents(eventsBeforeStart, topic, constTimestamp)

    val process        = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta)
    val deploymentData = deploymentDataWithOffset(OffsetResetStrategy.Restart)

    run(process, deploymentData) {
      createEvents(eventsAfterStart, topic, constTimestamp + 4)
      eventually {
        val results =
          KafkaSourceWithDeploymentParametersSpec.resultsHolders.sinkForSimpleJsonRecordResultsHolder.results
        results.map(_.id) shouldBe List("a", "b", "c", "d")
      }
    }
  }

  test("should start reading only new events when reset offset") {
    val topic = "kafka-from-latest"
    createTopic(topic)

    createEvents(eventsBeforeStart, topic, constTimestamp)

    val process        = createProcess(topic, SourceType.jsonKeyJsonValueWithMeta)
    val deploymentData = deploymentDataWithOffset(OffsetResetStrategy.Reset)

    run(process, deploymentData) {
      createEvents(eventsAfterStart, topic, constTimestamp + 4)
      eventually {
        val results =
          KafkaSourceWithDeploymentParametersSpec.resultsHolders.sinkForSimpleJsonRecordResultsHolder.results
        results.map(_.id) shouldBe List("d")
      }
    }
  }

  test("should continue where stopped") {
    // TODO
  }

  private def createEvents(events: List[ObjToSerialize], topic: String, startTimestamp: Long): Unit = {
    events.zipWithIndex.foreach { case (event, idx) =>
      pushMessage(objToSerializeSerializationSchema(topic), event, timestamp = startTimestamp + idx)
    }
  }

  private def deploymentDataWithOffset(offsetResetStrategy: OffsetResetStrategy): DeploymentData = {
    DeploymentData.empty
      .copy(nodesData =
        NodesDeploymentData(dataByNodeId =
          Map(
            NodeId("procSource") -> Map(
              FlinkKafkaSource.OFFSET_RESET_STRATEGY_PARAM_NAME.value -> offsetResetStrategy.toString
            )
          )
        )
      )
  }

}

object KafkaSourceWithDeploymentParametersSpec {

  private val resultsHolders = new ResultsHolders

}
