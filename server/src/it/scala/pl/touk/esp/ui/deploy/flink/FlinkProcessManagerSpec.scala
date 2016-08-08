package pl.touk.esp.ui.deploy.flink

import java.util.concurrent.TimeUnit

import argonaut.PrettyParams
import com.jayway.awaitility.Awaitility._
import com.jayway.awaitility.scala.AwaitilitySupport
import com.typesafe.config.{ConfigValueFactory, ConfigFactory}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.time.{Seconds, Span}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.process.sample.TestProcessConfigCreator
import pl.touk.esp.ui.sample.SampleProcess

import scala.concurrent.duration._

class FlinkProcessManagerSpec extends FlatSpec with Matchers with ScalaFutures with AwaitilitySupport {

  override implicit val patienceConfig = PatienceConfig(timeout = Span(5, Seconds))

  it should "deploy process in running flink" in {

    val resource: String = findJarPath()

    val process = SampleProcess.process
    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.spaces2)
    val id = process.id

    val config = ConfigFactory.load().withValue("flinkConfig.jarPath", ConfigValueFactory.fromAnyRef(resource))
    val processManager = FlinkProcessManager(config)

    assert(processManager.deploy(process.id, marshalled).isReadyWithin(100 seconds))

    val jobStatus = processManager.findJobStatus(id).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")

    assert(processManager.cancel(process.id).isReadyWithin(1 seconds))

    await().atMost(10, TimeUnit.SECONDS).until {
      val jobStatusCanceled = processManager.findJobStatus(id).futureValue
      jobStatusCanceled.isEmpty
    }
  }


  def findJarPath(): String = {
    val loader = classOf[TestProcessConfigCreator].getClassLoader
    loader.getResource("pl/touk/esp/process/sample/TestProcessConfigCreator.class")
      .getFile.replaceAll("!.*", "").replace("file:", "")
  }
}
