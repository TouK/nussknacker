package pl.touk.nussknacker.engine.management

import java.io.File
import java.nio.charset.StandardCharsets
import java.util.UUID

import argonaut.PrettyParams
import com.typesafe.config.ConfigValueFactory
import org.apache.kafka.clients.producer.ProducerRecord
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess}
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.concurrent.duration._

//TODO: get rid of at least some Thread.sleep
class FlinkProcessManagerSpec extends FlatSpec with Matchers with ScalaFutures with Eventually with DockerTest {

  import pl.touk.nussknacker.engine.kafka.KafkaUtils._

  val ProcessMarshaller = new ProcessMarshaller

  it should "deploy process in running flink" in {
    val processId = "runningFlink"

    val process = SampleProcess.prepareProcess(processId)

    deployProcessAndWaitIfRunning(process)

    cancel(processId)
  }

  it should "cancel before deployment" in {
    val processId = "cancelBeforeDeployment"

    val process = SampleProcess.prepareProcess(processId)

    deployProcessAndWaitIfRunning(process)

    deployProcessAndWaitIfRunning(process)

    cancel(processId)
  }


  it should "be able verify&redeploy kafka process" in {

    val processId = "verifyAndRedeploy"
    val outTopic = s"output-$processId"
    val inTopic = s"input-$processId"

    val kafkaProcess = SampleProcess.kafkaProcess(processId, inTopic)


    val kafkaClient = new KafkaClient(config.getString("processConfig.kafka.kafkaAddress"),
      config.getString("processConfig.kafka.zkAddress"))
    kafkaClient.createTopic(outTopic, 1)
    kafkaClient.createTopic(inTopic, 1)
    val consumer = kafkaClient.createConsumer().consume(outTopic)

    deployProcessAndWaitIfRunning(kafkaProcess)

    kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "1"))
    new String(consumer.head.message(), StandardCharsets.UTF_8) shouldBe "1"

    deployProcessAndWaitIfRunning(kafkaProcess)

    kafkaClient.producer.send(new ProducerRecord[String, String](inTopic, "2"))

    new String(consumer.head.message(), StandardCharsets.UTF_8) shouldBe "2"

    assert(processManager.cancel(kafkaProcess.id).isReadyWithin(10 seconds))
  }

  it should "save state when redeploying" in {

    val processId = "redeploy"
    val outTopic = s"output-$processId"

    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)

    val kafkaClient = new KafkaClient(config.getString("processConfig.kafka.kafkaAddress"),
      config.getString("processConfig.kafka.zkAddress"))
    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart)
    Thread.sleep(3000)
    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart)

    val messages = kafkaClient.createConsumer().consume(outTopic).take(2).toList

    val message = messages.last.message()
    new String(message, StandardCharsets.UTF_8) shouldBe "List(One element, One element)"

    cancel(processId)
  }

  it should "snapshot state and be able to deploy using it" in {

    val processId = "snapshot"
    val outTopic = s"output-$processId"

    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)

    val kafkaClient = new KafkaClient(config.getString("processConfig.kafka.kafkaAddress"),
      config.getString("processConfig.kafka.zkAddress"))

    kafkaClient.createTopic(outTopic, 1)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart)
    Thread.sleep(3000)

    val dir = new File("/tmp").toURI.toString
    val savepointPath = processManager.savepoint(processEmittingOneElementAfterStart.id, dir)
    assert(savepointPath.isReadyWithin(10 seconds))

    cancel(processId)

    deployProcessAndWaitIfRunning(processEmittingOneElementAfterStart, Some(savepointPath.futureValue))

    val messages = kafkaClient.createConsumer().consume(outTopic).take(2).toList

    val message = messages.last.message()
    new String(message, StandardCharsets.UTF_8) shouldBe "List(One element, One element)"

    cancel(processId)

  }

  it should "fail to redeploy if old is incompatible" in {
    val processId = "redeployFail"

    val process = StatefulSampleProcess.prepareProcessStringWithStringState(processId)

    deployProcessAndWaitIfRunning(process)

    val newMarshalled = ProcessMarshaller.toJson(StatefulSampleProcess.prepareProcessWithLongState(processId), PrettyParams.spaces2)
    val exception = processManager.deploy(process.id, GraphProcess(newMarshalled), None).failed.futureValue

    exception.getMessage shouldBe "State is incompatible, please stop process and start again with clean state"

    cancel(processId)
  }

  it should "deploy custom process" in {
    val processId = "customProcess"

    assert(processManager.deploy(processId, CustomProcess("pl.touk.nussknacker.engine.management.sample.CustomProcess"), None).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    cancel(processId)
  }


  it should "extract process definition" in {

    val definition = FlinkModelData(config).processDefinition

    definition.services should contain key "accountService"
  }

  it should "dispatch process signal to kafka" in {
    val signalsTopic = s"esp.signal-${UUID.randomUUID()}"
    val configWithSignals = config
      .withValue("processConfig.signals.topic", ConfigValueFactory.fromAnyRef(signalsTopic))
    val flinkModelData = FlinkModelData(configWithSignals)
    val processManager = FlinkProcessManager(flinkModelData, configWithSignals)

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

  private def deployProcessAndWaitIfRunning(process: EspProcess, savepointPath : Option[String] = None) = {
    val marshaled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)
    assert(processManager.deploy(process.id, GraphProcess(marshaled), savepointPath).isReadyWithin(100 seconds))
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