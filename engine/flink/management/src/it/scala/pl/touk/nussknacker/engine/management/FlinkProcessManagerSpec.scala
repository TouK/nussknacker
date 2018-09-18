package pl.touk.nussknacker.engine.management

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

import argonaut.PrettyParams
import com.typesafe.config.ConfigValueFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FlatSpec, FunSpec, FunSuite, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.concurrent.duration._

//TODO: get rid of at least some Thread.sleep
class FlinkProcessManagerSpec extends FunSuite with Matchers with ScalaFutures with Eventually with DockerTest {

  import pl.touk.nussknacker.engine.kafka.KafkaUtils._

  val ProcessMarshaller = new ProcessMarshaller

  test("deploy process in running flink") {
    val processId = "runningFlink"

    val process = SampleProcess.prepareProcess(processId)

    deployProcessAndWaitIfRunning(process, empty(process.id))

    cancel(processId)
  }

  test("cancel before deployment") {
    val processId = "cancelBeforeDeployment"

    val process = SampleProcess.prepareProcess(processId)

    deployProcessAndWaitIfRunning(process, empty(process.id))
    Thread.sleep(2000)

    deployProcessAndWaitIfRunning(process, empty(process.id))
    Thread.sleep(2000)

    cancel(processId)
  }


  test("be able verify&redeploy kafka process") {

    val processId = "verifyAndRedeploy"
    val outTopic = s"output-$processId"
    val inTopic = s"input-$processId"

    val kafkaProcess = SampleProcess.kafkaProcess(processId, inTopic)


    val kafkaClient: KafkaClient = createKafkaClient
    logger.info("Kafka client created")

    kafkaClient.createTopic(outTopic, 1)
    kafkaClient.createTopic(inTopic, 1)

    logger.info("Kafka topics created, deploying process")

    deployProcessAndWaitIfRunning(kafkaProcess, empty(processId))

    kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "1"))

    val consumer = kafkaClient.createConsumer()
    val messages1 = consumer.consume(outTopic)
    new String(messages1.take(1).toIterable.head.message(), StandardCharsets.UTF_8) shouldBe "1"

    deployProcessAndWaitIfRunning(kafkaProcess, empty(processId))

    kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "2"))

    val messages2 = kafkaClient.createConsumer().consume(outTopic)
    new String(messages2.take(2).toIterable.last.message(), StandardCharsets.UTF_8) shouldBe "2"

    assert(processManager.cancel(kafkaProcess.id).isReadyWithin(10 seconds))
  }

  test("save state when redeploying") {

    val processId = "redeploy"
    val outTopic = s"output-$processId"

    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)

    val kafkaClient: KafkaClient = createKafkaClient
    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))
    Thread.sleep(2000)
    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))

    val messages = kafkaClient.createConsumer().consume(outTopic).take(2).toList

    val message = messages.last.message()
    new String(message, StandardCharsets.UTF_8) shouldBe "List(One element, One element)"

    cancel(processId)
  }

  test("snapshot state and be able to deploy using it") {

    val processId = "snapshot"
    val outTopic = s"output-$processId"

    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)

    val kafkaClient: KafkaClient = createKafkaClient

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId))
    Thread.sleep(3000)

    val dir = new File("/tmp").toURI.toString
    val savepointPath = processManager.savepoint(processEmittingOneElementAfterStart.id, dir)
    assert(savepointPath.isReadyWithin(10 seconds))

    cancel(processId)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, empty(processId), Some(savepointPath.futureValue))

    val messages = kafkaClient.createConsumer().consume(outTopic).take(2).toList

    val message = messages.last.message()
    new String(message, StandardCharsets.UTF_8) shouldBe "List(One element, One element)"

    cancel(processId)

  }

  private def createKafkaClient = {
    val kafkaClient = new KafkaClient(config.getString("processConfig.kafka.kafkaAddress"),
      config.getString("processConfig.kafka.zkAddress"))
    kafkaClient
  }

  test("fail to redeploy if old is incompatible") {
    val processId = "redeployFail"

    val process = StatefulSampleProcess.prepareProcessStringWithStringState(processId)

    val kafkaClient: KafkaClient = createKafkaClient
    kafkaClient.createTopic(s"output-$processId", 1)

    deployProcessAndWaitIfRunning(process, empty(process.id))
    Thread.sleep(2000)
    logger.info("Starting to redeploy")

    val newMarshalled = ProcessMarshaller.toJson(StatefulSampleProcess.prepareProcessWithLongState(processId), PrettyParams.spaces2)
    val exception = processManager.deploy(empty(process.id), GraphProcess(newMarshalled), None).failed.futureValue

    exception.getMessage shouldBe "State is incompatible, please stop process and start again with clean state"

    cancel(processId)
  }

  def empty(processId: String): ProcessVersion = ProcessVersion.empty.copy(processId=processId)

  test("deploy custom process") {
    val processId = "customProcess"

    assert(processManager.deploy(empty(processId), CustomProcess("pl.touk.nussknacker.engine.management.sample.CustomProcess"), None).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    cancel(processId)
  }


  test("extract process definition") {

    val definition = FlinkModelData(config).processDefinition

    definition.services should contain key "accountService"
  }

  test("dispatch process signal to kafka") {
    val signalsTopic = s"esp.signal-${UUID.randomUUID()}"
    val configWithSignals = config
      .withValue("processConfig.signals.topic", ConfigValueFactory.fromAnyRef(signalsTopic))
    val flinkModelData = FlinkModelData(configWithSignals)

    val kafkaClient = new KafkaClient(
      configWithSignals.getString("processConfig.kafka.kafkaAddress"),
      configWithSignals.getString("processConfig.kafka.zkAddress"))
    val consumer = kafkaClient.createConsumer()

    flinkModelData.dispatchSignal("removeLockSignal", "test-process", Map("lockId" -> "test-lockId"))

    val readSignals = consumer.consume(signalsTopic).take(1).map(m => new String(m.message(), StandardCharsets.UTF_8)).toList
    val signalJson = argonaut.Parse.parse(readSignals(0)).right.get
    signalJson.field("processId").get.nospaces shouldBe "\"test-process\""
    signalJson.field("action").get.field("type").get.nospaces shouldBe "\"RemoveLock\""
    signalJson.field("action").get.field("lockId").get.nospaces shouldBe "\"test-lockId\""
  }

  private def deployProcessAndWaitIfRunning(process: EspProcess, processVersion: ProcessVersion, savepointPath : Option[String] = None) = {
    val marshaled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)
    assert(processManager.deploy(processVersion, GraphProcess(marshaled), savepointPath).isReadyWithin(100 seconds))
    Thread.sleep(1000)
    val jobStatus = processManager.findJobStatus(process.id).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")
  }

  private def cancel(processId: String) = {
    assert(processManager.cancel(processId).isReadyWithin(10 seconds))
    eventually {
      val jobStatusCanceled = processManager.findJobStatus(processId).futureValue
      if (jobStatusCanceled.nonEmpty)
        throw new IllegalStateException("Job still exists")
    }
  }
}