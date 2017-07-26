package pl.touk.nussknacker.engine.perftest

import java.util.UUID
import java.util.logging.{Level, Logger}

import akka.actor.ActorSystem
import argonaut.PrettyParams
import com.typesafe.config.{Config, ConfigFactory, ConfigValueFactory}
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.concurrent.{Eventually, ScalaFutures}
import org.scalatest.time.{Hours, Millis, Seconds, Span}
import org.scalatest.{BeforeAndAfterAll, Matchers, Suite}
import org.slf4j.bridge.SLF4JBridgeHandler
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.management.FlinkProcessManager
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.perftest.util.JmxClient.JmxConfig
import pl.touk.nussknacker.engine.perftest.util.{JmxMetricsCollector, MeasureTime}
import pl.touk.nussknacker.engine.perftest.util.JmxMetricsCollector.StoppedMetricsCollector

import scala.concurrent.duration._
import scala.language.implicitConversions

trait BasePerfTest extends ScalaFutures with BeforeAndAfterAll { suite: Suite with Matchers with Eventually =>

  protected implicit def durationToTimeout(duration: FiniteDuration): Timeout =
    Timeout(Span(duration.toMillis, Millis))

  override implicit val patienceConfig = PatienceConfig(
    timeout = Span(10, Seconds),
    interval = Span(100, Millis)
  )

  protected def baseTestName: String

  protected def configCreatorClassName: String

  protected def profile: String = "test"

  protected lazy val processId = s"perf.$baseTestName.${UUID.randomUUID()}"

  protected lazy val config: Config = {
//    ConfigFactory.load("application-local.conf")
    ConfigFactory.load()
      .withValue(
        s"$profile.processConfigCreatorClass",
        ConfigValueFactory.fromAnyRef(s"pl.touk.nussknacker.engine.perftest.sample.$configCreatorClassName")
      )
  }

  protected lazy val processManager = FlinkProcessManager(config)

  private var system: ActorSystem = _

  lazy val ProcessMarshaller = new ProcessMarshaller

  protected def collectMetricsIn[T](f: => T): (T, StoppedMetricsCollector, Long) = {
    val jmxCollector = JmxMetricsCollector(
      system,
      1 second,
      config.as[JmxConfig]("flinkConfig.taskmanager.jmx")
    )
    val ((r, time), metrics) = jmxCollector.collectIn {
      MeasureTime.in(f)
    }
    (r, metrics, time)
  }

  protected def withDeployedProcess[T](process: EspProcess)(f: => T) = {
    deployProcess(process)
    try {
      f
    } finally {
      cancelProcess()
    }
  }

  protected def deployProcess(process: EspProcess) = {
    val marshalled = ProcessMarshaller.toJson(process, PrettyParams.nospace)

    assert(processManager.deploy(processId, GraphProcess(marshalled), None).isReadyWithin(65 seconds))

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

  override protected def beforeAll(): Unit = {
    SLF4JBridgeHandler.removeHandlersForRootLogger()
    SLF4JBridgeHandler.install()
    Logger.getLogger("").setLevel(Level.ALL)
    system = ActorSystem(baseTestName, config)
  }

  override protected def afterAll() = {
    system.shutdown()
    system.awaitTermination()
  }

}