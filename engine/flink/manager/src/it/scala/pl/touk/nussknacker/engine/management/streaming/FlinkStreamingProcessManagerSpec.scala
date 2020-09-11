package pl.touk.nussknacker.engine.management.streaming

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.{Files, Paths}
import java.util.UUID

import com.typesafe.config.ConfigValueFactory
import io.circe.Json
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ProcessingTypeConfig
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.{CirceUtil, ProcessVersion}
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.definition.SignalDispatcher
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.management.{FlinkStateStatus, FlinkStreamingProcessManagerProvider}
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig

import scala.concurrent.duration._

//TODO: get rid of at least some Thread.sleep
class FlinkStreamingProcessManagerSpec extends FunSuite with Matchers with StreamingDockerTest {

  import pl.touk.nussknacker.engine.kafka.KafkaZookeeperUtils._

  override protected def classPath: String = s"./engine/flink/management/sample/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/managementSample.jar"

  test("deploy process in running flink") {
    val processId = "runningFlink"

    val version = ProcessVersion(15, ProcessName(processId), "user1", Some(13))
    val process = SampleProcess.prepareProcess(processId)

    deployProcessAndWaitIfRunning(process, version)

    processVersion(ProcessName(processId)) shouldBe Some(version)

    cancel(processId)
  }

  //manual test because it is hard to make it automatic
  //to run this test you have to add Thread.sleep(over 1 minute) to FlinkProcessMain.main method
  ignore("continue on timeout exception during process deploy") {
    val processId = "runningFlink"
    val process = SampleProcess.prepareProcess(processId)
    val marshaled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2
    val version = ProcessVersion(15, ProcessName(processId), "user1", Some(13))

    val deployedResponse = processManager.deploy(version, GraphProcess(marshaled), None, user = userToAct)

    assert(deployedResponse.isReadyWithin(70 seconds))
  }

  // TODO: unignore - currently quite often fail
  ignore("cancel before deployment") {
    val processId = "cancelBeforeDeployment"

    val process = SampleProcess.prepareProcess(processId)

    deployProcessAndWaitIfRunning(process, empty(process.id))
    Thread.sleep(2000)

    deployProcessAndWaitIfRunning(process, empty(process.id))
    Thread.sleep(2000)

    cancel(processId)
  }

  //this is for the case where e.g. we manually cancel flink job, or it fail and didn't restart...
  test("cancel of not existing job should not fail") {
    processManager.cancel(ProcessName("not existing job"), user = userToAct).futureValue shouldBe (())
  }

  test("be able verify&redeploy kafka process") {

    val processId = "verifyAndRedeploy"
    val outTopic = s"output-$processId"
    val inTopic = s"input-$processId"

    val kafkaProcess = SampleProcess.kafkaProcess(processId, inTopic)

    logger.info("Kafka client created")

    kafkaClient.createTopic(outTopic, 1)
    kafkaClient.createTopic(inTopic, 1)

    logger.info("Kafka topics created, deploying process")

    deployProcessAndWaitIfRunning(kafkaProcess, empty(processId))

    kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "1"))

    messagesFromTopic(outTopic, 1).head shouldBe "1"

    deployProcessAndWaitIfRunning(kafkaProcess, empty(processId))

    kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "2"))

    messagesFromTopic(outTopic, 2).last shouldBe "2"

    assert(processManager.cancel(ProcessName(kafkaProcess.id), user = userToAct).isReadyWithin(10 seconds))
  }

  // TODO: unignore - currently quite often fail during second deployProcessAndWaitIfRunning
  ignore("save state when redeploying") {

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

    cancel(processId)
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
    val savepointPathFuture = processManager.savepoint(ProcessName(processEmittingOneElementAfterStart.id), savepointDir = Some(savepointDir.toUri.toString))
        .map(_.path)
    assert(savepointPathFuture.isReadyWithin(10 seconds))
    val savepointPath = new URI(savepointPathFuture.futureValue)
    Paths.get(savepointPath).startsWith(savepointDir) shouldBe true

    cancel(processId)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId), Some(savepointPath.toString))

    val messages = messagesFromTopic(outTopic, 2)

    messages shouldBe List("List(One element)", "List(One element, One element)")

    cancel(processId)
  }

  test("should stop process and deploy it using savepoint") {
    val processId = "stop"
    val outTopic = s"output-$processId"
    kafkaClient.createTopic(outTopic)
    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))
    messagesFromTopic(outTopic, 1) shouldBe List("List(One element)")
    val savepointPath = processManager.stop(ProcessName(processId), savepointDir = None, user = userToAct).map(_.path)

    eventually {
      val status = processManager.findJobStatus(ProcessName(processId)).futureValue
      status.map(_.status) shouldBe Some(FlinkStateStatus.Finished)
    }

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId), Some(savepointPath.futureValue))

    messagesFromTopic(outTopic, 2) shouldBe List("List(One element)", "List(One element, One element)")

    cancel(processId)
  }

  test("fail to redeploy if old is incompatible") {
    val processId = "redeployFail"
    val outTopic = s"output-$processId"

    val process = StatefulSampleProcess.prepareProcessStringWithStringState(processId)

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(process, empty(process.id))
    messagesFromTopic(outTopic,1) shouldBe List("")

    logger.info("Starting to redeploy")

    val newMarshalled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(StatefulSampleProcess.prepareProcessWithLongState(processId))).spaces2
    val exception = processManager.deploy(empty(process.id), GraphProcess(newMarshalled), None, user = userToAct).failed.futureValue

    exception.getMessage shouldBe "State is incompatible, please stop process and start again with clean state"

    cancel(processId)
  }

  ignore("fail to redeploy if old state with mapAggregator is incompatible") {
    val processId = "redeployFail"
    val outTopic = s"output-$processId"

    val process = StatefulSampleProcess.processWithMapAggegator(processId, "#AGG.set")

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(process, empty(process.id))
    messagesFromTopic(outTopic,1) shouldBe List("test")

    logger.info("Starting to redeploy")

    val newMarshalled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(StatefulSampleProcess.processWithMapAggegator(processId, "#AGG.approxCardinality"))).spaces2
    val exception = processManager.deploy(empty(process.id), GraphProcess(newMarshalled), None, user = userToAct).failed.futureValue

    exception.getMessage shouldBe "State is incompatible, please stop process and start again with clean state"

    cancel(processId)
  }

  def empty(processId: String): ProcessVersion = ProcessVersion.empty.copy(processName = ProcessName(processId))

  test("deploy custom process") {
    val processId = "customProcess"

    assert(processManager.deploy(empty(processId), CustomProcess("pl.touk.nussknacker.engine.management.sample.CustomProcess"), None, user = userToAct).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(ProcessName(processId)).futureValue
    jobStatus.map(_.status.name) shouldBe Some(FlinkStateStatus.Running.name)
    jobStatus.map(_.status.isRunning) shouldBe Some(true)

    cancel(processId)
  }

  test("extract process definition") {
    val definition = processingTypeConfig.toModelData.processDefinition
    definition.services should contain key "accountService"
  }

  test("dispatch process signal to kafka") {
    val signalsTopic = s"esp.signal-${UUID.randomUUID()}"
    val configWithSignals = config
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

  private def deployProcessAndWaitIfRunning(process: EspProcess, processVersion: ProcessVersion, savepointPath : Option[String] = None) = {
    val marshaled = ProcessMarshaller.toJson(ProcessCanonizer.canonize(process)).spaces2
    assert(processManager.deploy(processVersion, GraphProcess(marshaled), savepointPath, user = userToAct).isReadyWithin(100 seconds))
    eventually {

      val jobStatus = processManager.findJobStatus(ProcessName(process.id)).futureValue
      logger.debug(s"Waiting for deploy: ${process.id}, $jobStatus")

      jobStatus.map(_.status.name) shouldBe Some(FlinkStateStatus.Running.name)
      jobStatus.map(_.status.isRunning) shouldBe Some(true)
    }
  }

  private def cancel(processId: String): Unit = {
    assert(processManager.cancel(ProcessName(processId), user = userToAct).isReadyWithin(10 seconds))
    eventually {
      val runningJobs = processManager
        .findJobStatus(ProcessName(processId))
        .futureValue
        .filter(_.status.isRunning)

      logger.debug(s"waiting for jobs: $processId, $runningJobs")
      if (runningJobs.nonEmpty) {
        throw new IllegalStateException("Job still exists")
      }
    }
  }

  private def processVersion(processId: ProcessName): Option[ProcessVersion] =
    processManager.findJobStatus(processId).futureValue.flatMap(_.version)
}
