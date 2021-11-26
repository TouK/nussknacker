package pl.touk.nussknacker.engine.requestresponse.management

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader

import java.io.File
import java.net.URL

class RequestResponseDeploymentManagerSpec extends FunSuite with VeryPatientScalaFutures with Matchers {

  import scala.concurrent.ExecutionContext.Implicits._

  test("it should parse test data and test request-response process") {
    val modelPath =
      new File(s"./engine/base/request-response/runtime/sample/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/requestResponseSample.jar")
    val modelData = ModelData(ConfigFactory.empty(), ModelClassLoader(modelPath.toURI.toURL :: Nil))

    val manager = new RequestResponseDeploymentManager(modelData, null)

    val process = ProcessMarshaller.toJson(ProcessCanonizer.canonize(EspProcessBuilder
        .id("")
        .path(None)
        .source("source", "request1-source")
        .processor("processor", "processorService")
        .emptySink("sink", "response-sink", "value" -> "'any'"))).noSpaces

    val results = manager.test(ProcessName("test1"), process, TestData.newLineSeparated("{\"field1\": \"a\", \"field2\": \"b\"}"), _ => null).futureValue

    results.nodeResults("sink") should have length 1
  }

}
