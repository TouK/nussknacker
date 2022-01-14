package pl.touk.nussknacker.engine.management.streaming

import com.typesafe.config.ConfigValueFactory
import io.circe.Json
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.api.deployment.DeploymentData
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.api.{CirceUtil, ProcessVersion}
import pl.touk.nussknacker.engine.definition.SignalDispatcher
import pl.touk.nussknacker.engine.management.FlinkStateStatus
import pl.touk.nussknacker.engine.marshall.ScenarioParser

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.UUID
import scala.concurrent.ExecutionContext.Implicits._
import scala.concurrent.duration._

class FlinkStreamingDeploymentManagerSpec extends FunSuite with Matchers with StreamingDockerTest {

  import pl.touk.nussknacker.engine.kafka.KafkaTestUtils._

  override protected def classPath: List[String] = ClassPaths.scalaClasspath

  private val defaultDeploymentData = DeploymentData.empty

  private val processId = ProcessId(765)

  test("deploy scenario in running flink") {
    val processName = "runningFlink"

    val version = ProcessVersion(VersionId(15), ProcessName(processName), processId, "user1", Some(13))
    val process = SampleProcess.prepareProcess(processName)

    deployProcessAndWaitIfRunning(process, version)

    processVersion(ProcessName(processName)) shouldBe Some(version)

    cancelProcess(processName)
  }

  //manual test because it is hard to make it automatic
  //to run this test you have to add Thread.sleep(over 1 minute) to FlinkProcessMain.main method
  ignore("continue on timeout exception during scenario deploy") {
    val processName = "runningFlink"
    val process = SampleProcess.prepareProcess(processName)
    val processGraph = ScenarioParser.toGraphProcess(process)
    val version = ProcessVersion(VersionId(15), ProcessName(processName), processId, "user1", Some(13))

    val deployedResponse = deploymentManager.deploy(version, defaultDeploymentData, processGraph, None)

    assert(deployedResponse.isReadyWithin(70 seconds))
  }

  //this is for the case where e.g. we manually cancel flink job, or it fail and didn't restart...
  test("cancel of not existing job should not fail") {
    deploymentManager.cancel(ProcessName("not existing job"), user = userToAct).futureValue shouldBe (())
  }

  test("be able verify&redeploy kafka scenario") {

    val processId = "verifyAndRedeploy"
    val outTopic = s"output-$processId"
    val inTopic = s"input-$processId"

    val kafkaProcess = SampleProcess.kafkaProcess(processId, inTopic)

    logger.info("Kafka client created")

    kafkaClient.createTopic(outTopic, 1)
    kafkaClient.createTopic(inTopic, 1)

    logger.info("Kafka topics created, deploying scenario")

    deployProcessAndWaitIfRunning(kafkaProcess, empty(processId))

    kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "1"))

    messagesFromTopic(outTopic, 1).head shouldBe "1"

    deployProcessAndWaitIfRunning(kafkaProcess, empty(processId))

    kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "2"))

    messagesFromTopic(outTopic, 2).last shouldBe "2"

    assert(deploymentManager.cancel(ProcessName(kafkaProcess.id), user = userToAct).isReadyWithin(10 seconds))
  }

  test("save state when redeploying") {

    val processId = "redeploy"
    val outTopic = s"output-$processId"

    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))
    //we wait for first element to appear in kafka to be sure it's processed, before we proceed to checkpoint
    messagesFromTopic(outTopic, 1) shouldBe List("List(One element)")

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))

    val messages = messagesFromTopic(outTopic, 2)

    messages shouldBe List("List(One element)", "List(One element, One element)")

    cancelProcess(processId)
  }

  test("snapshot state and be able to deploy using it") {
    val processId = "snapshot"
    val outTopic = s"output-$processId"

    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))
    //we wait for first element to appear in kafka to be sure it's processed, before we proceed to checkpoint
    messagesFromTopic(outTopic, 1) shouldBe List("List(One element)")

    val savepointDir = Files.createTempDirectory("customSavepoint")
    val savepointPathFuture = deploymentManager.savepoint(ProcessName(processEmittingOneElementAfterStart.id), savepointDir = Some(savepointDir.toUri.toString))
        .map(_.path)
    assert(savepointPathFuture.isReadyWithin(10 seconds))
    val savepointPath = new URI(savepointPathFuture.futureValue)
    Paths.get(savepointPath).startsWith(savepointDir) shouldBe true

    cancelProcess(processId)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId), Some(savepointPath.toString))

    val messages = messagesFromTopic(outTopic, 2)

    messages shouldBe List("List(One element)", "List(One element, One element)")

    cancelProcess(processId)
  }

  test("should stop scenario and deploy it using savepoint") {
    val processId = "stop"
    val outTopic = s"output-$processId"
    kafkaClient.createTopic(outTopic, 1)
    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))
    messagesFromTopic(outTopic, 1) shouldBe List("List(One element)")
    val savepointPath = deploymentManager.stop(ProcessName(processId), savepointDir = None, user = userToAct).map(_.path)

    eventually {
      val status = deploymentManager.findJobStatus(ProcessName(processId)).futureValue
      status.map(_.status) shouldBe Some(FlinkStateStatus.Finished)
    }

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId), Some(savepointPath.futureValue))

    messagesFromTopic(outTopic, 2) shouldBe List("List(One element)", "List(One element, One element)")

    cancelProcess(processId)
  }

  test("fail to redeploy if old is incompatible") {
    val processId = "redeployFail"
    val outTopic = s"output-$processId"

    val process = StatefulSampleProcess.prepareProcessStringWithStringState(processId)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(process, empty(process.id))
    messagesFromTopic(outTopic,1) shouldBe List("")

    logger.info("Starting to redeploy")

    val graphProcess = ScenarioParser.toGraphProcess(StatefulSampleProcess.prepareProcessWithLongState(processId))
    val exception = deploymentManager.deploy(empty(process.id), defaultDeploymentData, graphProcess, None).failed.futureValue

    exception.getMessage shouldBe "State is incompatible, please stop scenario and start again with clean state"

    cancelProcess(processId)
  }

  test("fail to redeploy if old state with mapAggregator is incompatible") {
    val processId = "redeployFailMapAggregator"
    val outTopic = s"output-$processId"

    val process = StatefulSampleProcess.processWithMapAggegator(processId, "#AGG.set")

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(process, empty(process.id))
    messagesFromTopic(outTopic,1) shouldBe List("test")

    logger.info("Starting to redeploy")

    val graphProcess = ScenarioParser.toGraphProcess(StatefulSampleProcess.processWithMapAggegator(processId, "#AGG.approxCardinality"))
    val exception = deploymentManager.deploy(empty(process.id), defaultDeploymentData,graphProcess, None).failed.futureValue

    exception.getMessage shouldBe "State is incompatible, please stop scenario and start again with clean state"

    cancelProcess(processId)
  }

  def empty(processId: String): ProcessVersion = ProcessVersion.empty.copy(processName = ProcessName(processId))

  test("extract scenario definition") {
    val definition = processingTypeConfig.toModelData.processDefinition
    definition.services should contain key "accountService"
  }

  test("dispatch scenario signal to kafka") {
    val signalsTopic = s"esp.signal-${UUID.randomUUID()}"
    val configWithSignals = configWithHostKafka
      .withValue("modelConfig.signals.topic", ConfigValueFactory.fromAnyRef(signalsTopic))
    val flinkModelData = ProcessingTypeConfig.read(configWithSignals).toModelData

    val consumer = kafkaClient.createConsumer()
    SignalDispatcher.dispatchSignal(flinkModelData)("removeLockSignal", "test-process", Map("lockId" -> "test-lockId"))

    val readSignals = consumer.consume(signalsTopic).take(1).map(m => new String(m.message(), StandardCharsets.UTF_8)).toList
    val signalJson = CirceUtil.decodeJsonUnsafe[Json](readSignals.head, "invalid signals").hcursor
    signalJson.downField("processId").focus shouldBe Some(Json.fromString("test-process"))
    signalJson.downField("action").downField("type").focus shouldBe Some(Json.fromString("RemoveLock"))
    signalJson.downField("action").downField("lockId").focus shouldBe Some(Json.fromString("test-lockId"))
  }

  private def messagesFromTopic(outTopic: String, count: Int): List[String] = {
    kafkaClient.createConsumer()
      .consume(outTopic)
      .map(_.message()).map(new String(_, StandardCharsets.UTF_8))
      .take(count).toList
  }

  private def processVersion(processId: ProcessName): Option[ProcessVersion] =
    deploymentManager.findJobStatus(processId).futureValue.flatMap(_.version)
}
