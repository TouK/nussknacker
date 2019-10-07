package pl.touk.nussknacker.engine.standalone.deployment

import java.nio.file.Files

import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.StandaloneProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.standalone.StandaloneProcessConfigCreator
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer
import pl.touk.nussknacker.engine.standalone.utils.metrics.NoOpMetricsProvider
import pl.touk.nussknacker.engine.standalone.utils.metrics.dropwizard.DropwizardMetricsProvider
import pl.touk.nussknacker.engine.testing.LocalModelData

class DeploymentServiceSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val tmpDir = Files.createTempDirectory("deploymentSpec")

  def createService() = new DeploymentService(new StandaloneContextPreparer(NoOpMetricsProvider),
    LocalModelData(ConfigFactory.load(), new StandaloneProcessConfigCreator),
    new FileProcessRepository(tmpDir.toFile))

  it should "preserve processes between deployments" in {

    val id = ProcessName("process1")
    val json = processWithIdAndPath(id, None)

    var service = createService()

    def restartApp() = {
      service = createService()
    }

    service.deploy(json).right.toOption shouldBe 'defined

    service.checkStatus(id) shouldBe 'defined

    restartApp()

    service.checkStatus(id) shouldBe 'defined
    service.cancel(id)
    service.checkStatus(id) shouldBe 'empty

    restartApp()
    service.checkStatus(id) shouldBe 'empty
  }

  it should "deploy on given path" in {
    val id1 = ProcessName("process1")
    val id2 = ProcessName("process2")
    val id3 = ProcessName("alamakota")

    val service = createService()

    service.deploy(processWithIdAndPath(id1, None))  shouldBe 'right
    service.getInterpreterByPath("process1") shouldBe 'defined
    service.checkStatus(id1) shouldBe 'defined

    service.deploy(processWithIdAndPath(id1, Some("alamakota"))) shouldBe 'right
    service.getInterpreterByPath("process1") shouldBe 'empty
    service.getInterpreterByPath("alamakota") shouldBe 'defined
    service.checkStatus(id1) shouldBe 'defined
    service.checkStatus(id3) shouldBe 'empty
  }

  it should "not allow deployment on same path" in {
    val id = ProcessName("process1")
    val id2 = ProcessName("process2")

    val service = createService()

    service.deploy(processWithIdAndPath(id, None))

    service.deploy(processWithIdAndPath(id2, Some("process1"))) shouldBe 'left

    service.deploy(processWithIdAndPath(id, Some("alamakota"))) shouldBe 'right

    service.deploy(processWithIdAndPath(id2, Some("process1"))) shouldBe 'right

  }

  private def processWithIdAndPath(processName: ProcessName, path: Option[String]) = {
    val canonical = ProcessCanonizer.canonize(StandaloneProcessBuilder
        .id(processName)
        .path(path)
        .exceptionHandler()
        .source("start", "request1-post-source")
        .sink("endNodeIID", "''", "response-sink"))
    DeploymentData(ProcessMarshaller.toJson(canonical).spaces2, 0, ProcessVersion.empty.copy(processName = processName))
  }
}
