package pl.touk.esp.engine.perftest

import java.util.UUID

import argonaut.PrettyParams
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import org.scalatest.{Matchers, Suite}
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Millis, Seconds, Span}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.management.FlinkProcessManager
import pl.touk.esp.engine.management.util.JarFileFinder
import pl.touk.esp.engine.marshall.ProcessMarshaller

import scala.concurrent.duration._

trait BasePerfTest extends ScalaFutures { suite: Suite with Matchers with Eventually =>

  override implicit val patienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

  protected def baseTestName: String

  protected def configCreatorClass: Class[_]

  protected def profile: String = "test"

  protected lazy val processId = s"perf.$baseTestName.${UUID.randomUUID()}"

  protected lazy val config: Config = {
    val resource = JarFileFinder.findJarPath(configCreatorClass)
    ConfigFactory.load()
      .withValue("flinkConfig.jarPath", ConfigValueFactory.fromAnyRef(resource))
      .withValue(s"$profile.processConfigCreatorClass", ConfigValueFactory.fromAnyRef(configCreatorClass.getName))
  }

  protected lazy val processManager = FlinkProcessManager(config)

  protected def deployProcess(process: EspProcess) = {
    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.nospace)

    assert(processManager.deploy(processId, marshalled).isReadyWithin(65 seconds))

    val jobStatus = processManager.findJobStatus(processId).futureValue
    jobStatus.map(_.status) shouldBe Some("RUNNING")
  }

  protected def cancelProcess(): Unit = {
    assert(processManager.cancel(processId).isReadyWithin(1 seconds))

    eventually {
      val jobStatusCanceled = processManager.findJobStatus(processId).futureValue
      if (jobStatusCanceled.nonEmpty)
        throw new IllegalStateException("Job still exists")
    }
  }

}