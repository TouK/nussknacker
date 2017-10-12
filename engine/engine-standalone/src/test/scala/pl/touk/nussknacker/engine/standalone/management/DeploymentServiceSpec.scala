package pl.touk.nussknacker.engine.standalone.management

import java.nio.file.Files

import argonaut.PrettyParams
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.ConfigFactory
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.nussknacker.engine.build.StandaloneProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.spel
import pl.touk.nussknacker.engine.standalone.StandaloneProcessConfigCreator
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer
import pl.touk.nussknacker.engine.testing.LocalModelData

class DeploymentServiceSpec extends FlatSpec with Matchers {

  import spel.Implicits._

  import scala.concurrent.ExecutionContext.Implicits.global

  private val tmpDir = Files.createTempDirectory("deploymentSpec")

  def createService() = new DeploymentService(new StandaloneContextPreparer(new MetricRegistry),
    LocalModelData(ConfigFactory.load(), new StandaloneProcessConfigCreator),
    new FileProcessRepository(tmpDir.toFile))

  it should "preserve processes between deployments" in {

    val id = "process1"
    val json = processWithIdAndPath(id, None)

    var service = createService()

    def restartApp() = {
      service = createService()
    }

    service.deploy(id, json).right.toOption shouldBe 'defined

    service.checkStatus(id) shouldBe 'defined

    restartApp()

    service.checkStatus(id) shouldBe 'defined
    service.cancel(id)
    service.checkStatus(id) shouldBe 'empty

    restartApp()
    service.checkStatus(id) shouldBe 'empty
  }

  it should "deploy on given path" in {
    val id = "process1"

    val service = createService()

    service.deploy(id, processWithIdAndPath(id, None))  shouldBe 'right

    service.getInterpreterByPath("process1") shouldBe 'defined

    service.checkStatus("process1") shouldBe 'defined

    service.deploy(id, processWithIdAndPath(id, Some("alamakota"))) shouldBe 'right

    service.getInterpreterByPath("process1") shouldBe 'empty

    service.getInterpreterByPath("alamakota") shouldBe 'defined

    service.checkStatus("process1") shouldBe 'defined

    service.checkStatus("alamakota") shouldBe 'empty

  }

  it should "not allowe deployment on same path" in {
    val id = "process1"
    val id2 = "process2"


    val service = createService()

    service.deploy(id, processWithIdAndPath(id, None))

    service.deploy(id2, processWithIdAndPath(id2, Some("process1"))) shouldBe 'left

    service.deploy(id, processWithIdAndPath(id, Some("alamakota"))) shouldBe 'right

    service.deploy(id2, processWithIdAndPath(id2, Some("process1"))) shouldBe 'right

  }

  private def processWithIdAndPath(id: String, path: Option[String]) = {
    val canonical = ProcessCanonizer.canonize(StandaloneProcessBuilder
        .id(id)
          .path(path)
        .exceptionHandler()
        .source("start", "request1-source")
        .sink("endNodeIID", "''", "response-sink"))
    new ProcessMarshaller().toJson(canonical, PrettyParams.spaces2)
  }
}
