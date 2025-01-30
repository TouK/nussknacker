package pl.touk.nussknacker.defaultmodel

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import io.confluent.kafka.schemaregistry.json.JsonSchema
import org.apache.flink.api.common.JobExecutionResult
import org.apache.flink.core.execution.SavepointFormatType
import org.apache.flink.runtime.jobgraph.SavepointRestoreSettings
import pl.touk.nussknacker.defaultmodel.SampleSchemas.RecordSchemaV1
import pl.touk.nussknacker.defaultmodel.StateCompatibilityTest.{InputEvent, OutputEvent}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.TopicName
import pl.touk.nussknacker.engine.api.validation.ValidationMode
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.schemedkafka.KafkaUniversalComponentTransformer
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.ExistingSchemaVersion
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.engine.version.BuildInfo
import pl.touk.nussknacker.test.PatientScalaFutures

import java.net.URI
import java.nio.file.{Files, Paths}
import java.time.LocalDate

object StateCompatibilityTest {

  @JsonCodec(decodeOnly = true)
  case class InputEvent(first: String, last: String)

  @JsonCodec(decodeOnly = true)
  case class OutputEvent(input: InputEvent, previousInput: InputEvent)

}

/**
  * Verifies whether a scenario can be run from savepoint created earlier, e.g. by older Nussknacker or older process config creator.
  *
  * Important:
  * A compliance with previous-snapshot strongly depends on serialVersionUID (all snapshot classes implement Serializable).
  * and this serialVersionUID should be explicitly provided in all savepoint-able classes.
  * @see description in [[pl.touk.nussknacker.engine.process.util.Serializers]]
  * @see https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/io/Serializable.html
  */
class StateCompatibilityTest extends FlinkWithKafkaSuite with PatientScalaFutures with LazyLogging {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils.richConsumer
  import pl.touk.nussknacker.engine.spel.SpelExtension._

  import scala.jdk.CollectionConverters._

  private val inTopic  = "state.compatibility.input"
  private val outTopic = "state.compatibility.output"

  private val savepointDir = {
    val resourcesDir = Paths.get(s"src/test/resources/state-compatibility/${ScalaMajorVersionConfig.scalaMajorVersion}")
    if (Files.exists(resourcesDir)) {
      // Working directory is module root directory.
      resourcesDir
    } else {
      // Working directory is root project.
      val path = Paths.get("engine/flink/tests").resolve(resourcesDir)
      path
    }
  }

  private def stateCompatibilityProcess(inTopic: TopicName.ForSource, outTopic: TopicName.ForSink) = ScenarioBuilder
    .streaming("stateCompatibilityTest")
    .parallelism(1)
    .source(
      "start",
      "kafka",
      KafkaUniversalComponentTransformer.topicParamName.value -> s"'${inTopic.name}'".spel,
      KafkaUniversalComponentTransformer.schemaVersionParamName.value -> versionOptionParam(
        ExistingSchemaVersion(1)
      ).spel
    )
    .customNode(
      "previousValue",
      "previousValue",
      "previousValue",
      "groupBy" -> "'constant'".spel,
      "value"   -> "#input".spel
    )
    .emptySink(
      "sink",
      "kafka",
      KafkaUniversalComponentTransformer.topicParamName.value              -> s"'${outTopic.name}'".spel,
      KafkaUniversalComponentTransformer.schemaVersionParamName.value      -> "'latest'".spel,
      KafkaUniversalComponentTransformer.sinkKeyParamName.value            -> "".spel,
      KafkaUniversalComponentTransformer.sinkRawEditorParamName.value      -> s"true".spel,
      KafkaUniversalComponentTransformer.sinkValidationModeParamName.value -> s"'${ValidationMode.lax.name}'".spel,
      KafkaUniversalComponentTransformer.sinkValueParamName.value -> "{ input: #input, previousInput: #previousValue }".spel
    )

  private val event1: InputEvent = InputEvent("Jan", "Kowalski")
  private val event2             = InputEvent("Zenon", "Nowak")

  private val JsonSchemaV1 = new JsonSchema("""|{
       |  "type": "object",
       |  "properties": {
       |    "input" :  {
       |      "type": "object",
       |      "properties": {
       |        "first" : { "type": "string" },
       |        "last" : { "type": "string" }
       |      },
       |      "required": ["first", "last"]
       |    },
       |    "previousInput" :  {
       |      "type": "object",
       |      "properties": {
       |        "first" : { "type": "string" },
       |        "last" : { "type": "string" }
       |      },
       |      "required": ["first", "last"]
       |    }
       |  },
       |  "required": ["input"]
       |}
       |""".stripMargin)

  /**
    * When previous snapshot compatibility breaks - (read 'should restore from snapshot' fails):
    * 1. un-ignore this test and run manually to create new savepoint used in the other test
    * 2. remove old snapshot (tests in this class require ONLY ONE savepoint)
    * 3. go back to ignore :)
    */
  ignore("should create savepoint and save to disk") {
    val inputTopicConfig  = createAndRegisterAvroTopicConfig(inTopic, RecordSchemaV1)
    val outputTopicConfig = createAndRegisterTopicConfig(outTopic, JsonSchemaV1)

    sendAvro(givenMatchingAvroObj, inputTopicConfig.input)

    run(
      stateCompatibilityProcess(inputTopicConfig.input, outputTopicConfig.output),
      { jobExecutionResult =>
        verifyOutputEvent(outputTopicConfig.output, input = event1, previousInput = event1)

        val savepointLocation = eventually {
          flinkMiniCluster.miniCluster
            .triggerSavepoint(jobExecutionResult.getJobID, savepointDir.toString, false, SavepointFormatType.DEFAULT)
            .get()
        }

        saveSnapshot(savepointLocation)
      }
    )
  }

  test("should restore from snapshot") {
    val inputTopicConfig  = createAndRegisterAvroTopicConfig(inTopic, RecordSchemaV1)
    val outputTopicConfig = createAndRegisterTopicConfig(outTopic, JsonSchemaV1)

    val existingSavepointLocation = Files.list(savepointDir).iterator().asScala.toList.head
    val process1                  = stateCompatibilityProcess(inputTopicConfig.input, outputTopicConfig.output)
    flinkMiniCluster.withExecutionEnvironment { env =>
      registrar.register(env.env, process1, ProcessVersion.empty, DeploymentData.empty)
      val streamGraph           = env.env.getStreamGraph
      val allowNonRestoredState = false
      streamGraph.setSavepointRestoreSettings(
        SavepointRestoreSettings.forPath(existingSavepointLocation.toString, allowNonRestoredState)
      )
      // Send one artificial message to mimic offsets saved in savepoint from the above test because kafka commit cannot be performed.
      sendAvro(givenMatchingAvroObj, inputTopicConfig.input).futureValue

      val jobExecutionResult = env.env.execute(streamGraph)
      env.waitForStart(jobExecutionResult.getJobID)()
      sendAvro(givenNotMatchingAvroObj, inputTopicConfig.input).futureValue

      env.assertJobNotFailing(jobExecutionResult.getJobID)
      verifyOutputEvent(outputTopicConfig.output, input = event2, previousInput = event1)
      env.stopJob(jobExecutionResult.getJobID)
    }
  }

  private def verifyOutputEvent(outTopic: TopicName.ForSink, input: InputEvent, previousInput: InputEvent): Unit = {
    val outputEvent = kafkaClient.createConsumer().consumeWithJson[OutputEvent](outTopic.name).take(1).head.message()
    outputEvent.input shouldBe input
    outputEvent.previousInput shouldBe previousInput
  }

  private def saveSnapshot(savepointLocation: String): Unit = {
    val savepointPath          = Paths.get(new URI(savepointLocation))
    val savepointName          = s"${LocalDate.now()}_${BuildInfo.gitCommit}"
    val versionedSavepointPath = savepointDir.resolve(savepointName)
    Files.move(savepointPath, versionedSavepointPath)
    logger.info("Saved savepoint in: '{}'", versionedSavepointPath)
  }

  private def run(process: CanonicalProcess, action: JobExecutionResult => Unit): Unit = {
    flinkMiniCluster.withExecutionEnvironment { env =>
      registrar.register(env.env, process, ProcessVersion.empty, DeploymentData.empty)
      env.withJobRunning(process.name.value, action)
    }
  }

}
