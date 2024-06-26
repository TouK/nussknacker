package pl.touk.nussknacker.engine.lite.kafka

import org.scalatest.TestSuite
import pl.touk.nussknacker.engine.api.process.{ProcessName, TopicName}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.lite.utils.NuRuntimeTestUtils

import java.io.File

trait NuKafkaRuntimeTestMixin { self: TestSuite =>

  protected def kafkaBoostrapServer: String

  protected def prepareTestCaseFixture(
      scenarioName: ProcessName,
      prepareScenario: (TopicName.OfSource, TopicName.OfSink) => CanonicalProcess
  ): NuKafkaRuntimeTestTestCaseFixture = {
    val testCaseId  = NuRuntimeTestUtils.testCaseId(self.suiteName, scenarioName)
    val inputTopic  = TopicName.OfSource(testCaseId + "-input")
    val outputTopic = TopicName.OfSink(testCaseId + "-output")
    val errorTopic  = testCaseId + "-error"
    kafkaClient.createTopic(inputTopic.name)
    kafkaClient.createTopic(outputTopic.name, 1)
    kafkaClient.createTopic(errorTopic, 1)
    val scenarioFile = NuRuntimeTestUtils.saveScenarioToTmp(prepareScenario(inputTopic, outputTopic), testCaseId)
    NuKafkaRuntimeTestTestCaseFixture(inputTopic, outputTopic, errorTopic, scenarioFile)
  }

  protected def kafkaClient: KafkaClient

}

final case class NuKafkaRuntimeTestTestCaseFixture(
    inputTopic: TopicName.OfSource,
    outputTopic: TopicName.OfSink,
    errorTopic: String,
    scenarioFile: File
)
