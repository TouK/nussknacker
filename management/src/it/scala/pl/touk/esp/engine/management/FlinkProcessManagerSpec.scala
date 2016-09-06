package pl.touk.esp.engine.management

import java.util.UUID

import argonaut.PrettyParams
import com.typesafe.config.ConfigFactory
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.kafka.KafkaClient
import pl.touk.esp.engine.marshall.ProcessMarshaller

import scala.concurrent.duration._
import pl.touk.esp.engine.kafka.KafkaUtils._

class FlinkProcessManagerSpec extends FlatSpec with Matchers with ScalaFutures with Eventually {

  override implicit val patienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

  it should "deploy process in running flink" in {
    val processId = UUID.randomUUID().toString

    val process = SampleProcess.prepareProcess(processId)
    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)

    val config = ConfigFactory.load()
    val processManager = FlinkProcessManager(config)

    assert(processManager.deploy(process.id, marshalled).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    assert(processManager.cancel(process.id).isReadyWithin(1 seconds))

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

    val config = ConfigFactory.load()
    val processManager = FlinkProcessManager(config)

    assert(processManager.deploy(process.id, marshalled).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    Thread.sleep(2000)

    assert(processManager.deploy(process.id, marshalled).isReadyWithin(100 seconds))

    val jobStatus2 = processManager.findJobStatus(processId).futureValue
    jobStatus2.map(_.status) shouldBe Some("RUNNING")

    assert(processManager.cancel(process.id).isReadyWithin(1 seconds))

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

    val config = ConfigFactory.load()
    val processManager = FlinkProcessManager(config)


    val kafkaClient = new KafkaClient(config.getString("prod.kafka.kafkaAddress"),
      config.getString("prod.kafka.zkAddress"))
    kafkaClient.createTopic(outTopic)


    assert(processManager.deploy(processEmittingOneElementAfterStart.id, marshalledProcess).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    Thread.sleep(2000)

    assert(processManager.deploy(processEmittingOneElementAfterStart.id, marshalledProcess).isReadyWithin(100 seconds))

    val jobStatus2 = processManager.findJobStatus(processId).futureValue
    jobStatus2.map(_.status) shouldBe Some("RUNNING")

    val message = kafkaClient.createConsumer().consume(outTopic).take(1).head.message()
    new String(message) shouldBe "SampleFold(2)"

    assert(processManager.cancel(processEmittingOneElementAfterStart.id).isReadyWithin(1 seconds))

  }

  it should "redeploy with clean state if old is incompatible" in {
    val processId = UUID.randomUUID().toString
    val outTopic = s"output-$processId"

    val process = StatefulSampleProcess.prepareProcess(processId)
    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)


    val config = ConfigFactory.load()
    val processManager = FlinkProcessManager(config)


    val kafkaClient = new KafkaClient(config.getString("prod.kafka.kafkaAddress"),
      config.getString("prod.kafka.zkAddress"))
    kafkaClient.createTopic(outTopic)


    assert(processManager.deploy(process.id, marshalled).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    Thread.sleep(1000)

    val newMarshalled = ProcessMarshaller.toJson(SampleProcess.prepareProcess(processId), PrettyParams.spaces2)

    assert(processManager.deploy(process.id, newMarshalled).isReadyWithin(100 seconds))

    val jobStatus2 = processManager.findJobStatus(processId).futureValue
    jobStatus2.map(_.status) shouldBe Some("RUNNING")

    assert(processManager.cancel(process.id).isReadyWithin(1 seconds))
  }

}