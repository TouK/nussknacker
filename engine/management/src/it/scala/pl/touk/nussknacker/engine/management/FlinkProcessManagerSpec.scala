package pl.touk.nussknacker.engine.management

import java.io.File
import java.util.UUID

import argonaut.PrettyParams
import com.typesafe.config.ConfigValueFactory
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess}
import pl.touk.nussknacker.engine.kafka.KafkaClient
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller

import scala.concurrent.duration._

//TODO: get rid of at least some Thread.sleep
class FlinkProcessManagerSpec extends FlatSpec with Matchers with ScalaFutures with Eventually with DockerTest {

  import pl.touk.nussknacker.engine.kafka.KafkaUtils._

  val ProcessMarshaller = new ProcessMarshaller

  it should "deploy process in running flink" in {

    val processId = UUID.randomUUID().toString

    val process = SampleProcess.prepareProcess(processId)
    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)

    val processManager = FlinkProcessManager(config)

    assert(processManager.deploy(process.id, GraphProcess(marshalled), None).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    assert(processManager.cancel(process.id).isReadyWithin(10 seconds))

    eventually {
      val jobStatusCanceled = processManager.findJobStatus(processId).futureValue
      if (jobStatusCanceled.nonEmpty)
        throw new IllegalStateException("Job still exists")
    }
  }

  it should "cancel before deployment" in {
    val processId = UUID.randomUUID().toString

    val process = SampleProcess.prepareProcess(processId)
    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)

    val processManager = FlinkProcessManager(config)

    assert(processManager.deploy(process.id, GraphProcess(marshalled), None).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    Thread.sleep(2000)

    assert(processManager.deploy(process.id, GraphProcess(marshalled), None).isReadyWithin(100 seconds))

    val jobStatus2 = processManager.findJobStatus(processId).futureValue
    jobStatus2.map(_.status) shouldBe Some("RUNNING")

    assert(processManager.cancel(process.id).isReadyWithin(10 seconds))

    eventually {
      val jobStatusCanceled = processManager.findJobStatus(processId).futureValue
      if (jobStatusCanceled.nonEmpty)
        throw new IllegalStateException("Job still exists")
    }
  }

  it should "save state when redeploying" in {

    val processId = UUID.randomUUID().toString
    val outTopic = s"output-$processId"

    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)
    val marshalledProcess = ProcessMarshaller.toJson(processEmittingOneElementAfterStart, PrettyParams.spaces2)

    val processManager = FlinkProcessManager(config)


    val kafkaClient = new KafkaClient(config.getString("prod.kafka.kafkaAddress"),
      config.getString("prod.kafka.zkAddress"))
    kafkaClient.createTopic(outTopic, 1)


    assert(processManager.deploy(processEmittingOneElementAfterStart.id, GraphProcess(marshalledProcess), None).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    Thread.sleep(2000)

    assert(processManager.deploy(processEmittingOneElementAfterStart.id, GraphProcess(marshalledProcess), None).isReadyWithin(100 seconds))

    val jobStatus2 = processManager.findJobStatus(processId).futureValue
    jobStatus2.map(_.status) shouldBe Some("RUNNING")

    val messages = kafkaClient.createConsumer().consume(outTopic).take(2).toList
    println("save state when redeploying messages: " + messages.map(m => new String(m.message())).mkString("\n"))

    val message = messages.last.message()
    new String(message) shouldBe "List(One element, One element)"

    assert(processManager.cancel(processEmittingOneElementAfterStart.id).isReadyWithin(10 seconds))

  }

  it should "snapshot state and be able to deploy using it" in {

    val processId = UUID.randomUUID().toString
    val outTopic = s"output-$processId"

    val processEmittingOneElementAfterStart = StatefulSampleProcess.prepareProcess(processId)
    val marshalledProcess = ProcessMarshaller.toJson(processEmittingOneElementAfterStart, PrettyParams.spaces2)

    val processManager = FlinkProcessManager(config)


    val kafkaClient = new KafkaClient(config.getString("prod.kafka.kafkaAddress"),
      config.getString("prod.kafka.zkAddress"))

    kafkaClient.createTopic(outTopic, 1)

    assert(processManager.deploy(processEmittingOneElementAfterStart.id, GraphProcess(marshalledProcess), None).isReadyWithin(100 seconds))
    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")
    Thread.sleep(4000)

    val dir = new File("/tmp").toURI.toString
    val savepointPath = processManager.savepoint(processEmittingOneElementAfterStart.id, dir)
    assert(savepointPath.isReadyWithin(10 seconds))

    assert(processManager.cancel(processEmittingOneElementAfterStart.id).isReadyWithin(10 seconds))
    Thread.sleep(4000)

    assert(savepointPath.flatMap(path => processManager.deploy(processEmittingOneElementAfterStart.id,
      GraphProcess(marshalledProcess), Some(path))).isReadyWithin(100 seconds))

    val jobStatus2 = processManager.findJobStatus(processId).futureValue
    jobStatus2.map(_.status) shouldBe Some("RUNNING")

    val messages = kafkaClient.createConsumer().consume(outTopic).take(2).toList

    val message = messages.last.message()
    new String(message) shouldBe "List(One element, One element)"

    assert(processManager.cancel(processEmittingOneElementAfterStart.id).isReadyWithin(10 seconds))

  }

  it should "redeploy with clean state if old is incompatible" in {
    val processId = UUID.randomUUID().toString
    val outTopic = s"output-$processId"

    val process = StatefulSampleProcess.prepareProcess(processId)
    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)


    val processManager = FlinkProcessManager(config)


    val kafkaClient = new KafkaClient(config.getString("prod.kafka.kafkaAddress"),
      config.getString("prod.kafka.zkAddress"))
    kafkaClient.createTopic(outTopic)


    assert(processManager.deploy(process.id, GraphProcess(marshalled), None).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    Thread.sleep(3000)

    val newMarshalled = ProcessMarshaller.toJson(SampleProcess.prepareProcess(processId), PrettyParams.spaces2)

    assert(processManager.deploy(process.id, GraphProcess(newMarshalled), None).isReadyWithin(100 seconds))

    val jobStatus2 = processManager.findJobStatus(processId).futureValue
    jobStatus2.map(_.status) shouldBe Some("RUNNING")

    assert(processManager.cancel(process.id).isReadyWithin(10 seconds))
  }

  it should "deploy custom process" in {
    val processId = UUID.randomUUID().toString

    val processManager = FlinkProcessManager(config)

    assert(processManager.deploy(processId, CustomProcess("pl.touk.nussknacker.engine.management.sample.CustomProcess"), None).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    assert(processManager.cancel(processId).isReadyWithin(30 seconds))

    eventually {
      val jobStatusCanceled = processManager.findJobStatus(processId).futureValue
      if (jobStatusCanceled.nonEmpty)
        throw new IllegalStateException("Job still exists")
    }
  }

  it should "extract process definition" in {
    val processManager = FlinkProcessManager(config)


    val definition = processManager.getProcessDefinition

    definition.services should contain key "accountService"
  }

  it should "dispatch process signal to kafka" in {
    val signalsTopic = s"esp.signal-${UUID.randomUUID()}"
    val configWithSignals = config
      .withValue("prod.signals.topic", ConfigValueFactory.fromAnyRef(signalsTopic))
    val processManager = FlinkProcessManager(configWithSignals)
    val kafkaClient = new KafkaClient(
      configWithSignals.getString("prod.kafka.kafkaAddress"),
      configWithSignals.getString("prod.kafka.zkAddress"))
    val consumer = kafkaClient.createConsumer()

    processManager.dispatchSignal("removeLockSignal", "test-process", Map("lockId" -> "test-lockId"))

    val readSignals = consumer.consume(signalsTopic).take(1).map(m => new String(m.message())).toList
    val signalJson = argonaut.Parse.parse(readSignals(0)).right.get
    signalJson.field("processId").get.nospaces shouldBe "\"test-process\""
    signalJson.field("action").get.field("type").get.nospaces shouldBe "\"RemoveLock\""
    signalJson.field("action").get.field("lockId").get.nospaces shouldBe "\"test-lockId\""
  }                    
}