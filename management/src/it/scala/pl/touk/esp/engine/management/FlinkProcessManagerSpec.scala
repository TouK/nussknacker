package pl.touk.esp.engine.management

import java.util.UUID

import argonaut.PrettyParams
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.management.sample.TestProcessConfigCreator
import pl.touk.esp.engine.management.util.JarFileFinder
import pl.touk.esp.engine.marshall.ProcessMarshaller

import scala.concurrent.duration._

class FlinkProcessManagerSpec extends FlatSpec with Matchers with ScalaFutures with Eventually {

  override implicit val patienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

  it should "deploy process in running flink" in {

    val processId = UUID.randomUUID().toString

    val resource = JarFileFinder.findJarPath(classOf[TestProcessConfigCreator])

    val process = SampleProcess.prepareProcess(processId)
    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)

    val config = ConfigFactory.load().withValue("flinkConfig.jarPath", ConfigValueFactory.fromAnyRef(resource))
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

}
