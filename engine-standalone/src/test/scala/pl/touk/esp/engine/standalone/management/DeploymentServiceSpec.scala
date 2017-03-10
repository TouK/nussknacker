package pl.touk.esp.engine.standalone.management

import java.nio.file.Files

import argonaut.PrettyParams
import com.typesafe.config.{ConfigFactory, ConfigValueFactory}
import org.scalatest.{FlatSpec, Matchers}
import pl.touk.esp.engine.build.EspProcessBuilder
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.spel
import pl.touk.esp.engine.standalone.StandaloneProcessConfigCreator

class DeploymentServiceSpec extends FlatSpec with Matchers {

  import scala.concurrent.ExecutionContext.Implicits.global
  import spel.Implicits._

  val id = "process1"

  val canonical = ProcessCanonizer.canonize(EspProcessBuilder
    .id(id)
    .exceptionHandler()
    .source("start", "request1-source")
    .sink("endNodeIID", "''", "response-sink"))

  val json = new ProcessMarshaller().toJson(canonical, PrettyParams.spaces2)


  val tmpDir = Files.createTempDirectory("deploymentSpec")


  def createService() = DeploymentService(new StandaloneProcessConfigCreator,
    ConfigFactory.load().withValue("standaloneEngineProcessLocation", ConfigValueFactory.fromAnyRef(tmpDir.toFile.getAbsolutePath)))

  it should "preserve processes between deployments" in {

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

}
