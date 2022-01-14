package pl.touk.nussknacker.engine.requestresponse.management

import com.typesafe.config.ConfigFactory
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{NodeResult, ResultContext, TestData}
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.runtimecontext.IncContextIdGenerator
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.build.EspProcessBuilder
import pl.touk.nussknacker.engine.marshall.ScenarioParser
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.util.config.ScalaMajorVersionConfig
import pl.touk.nussknacker.engine.util.loader.ModelClassLoader
import pl.touk.nussknacker.test.VeryPatientScalaFutures

import java.io.File

class RequestResponseDeploymentManagerSpec extends FunSuite with VeryPatientScalaFutures with Matchers {

  import scala.concurrent.ExecutionContext.Implicits._

  test("it should parse test data and test request-response process") {
    val modelPath = List(
      new File(s"./defaultModel/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/defaultModel.jar"),
      new File(s"./engine/lite/components/request-response/target/scala-${ScalaMajorVersionConfig.scalaMajorVersion}/liteRequestResponse.jar")
    )
    val modelData = ModelData(ConfigFactory.empty(), ModelClassLoader(modelPath.map(_.toURI.toURL)))

    val manager = new RequestResponseDeploymentManager(modelData, null)

    val schema = """'{ "properties": { "field1": {"type":"string"}, "field2": {"type":"string"} }}'"""

    val process = EspProcessBuilder
        .id("tst")
        .path(None)
        .source("source", "request", "schema" -> schema)
        .filter("ddd", "#input != null")
        .emptySink("sink", "response", "value" -> "#input.field1")

    val graphProcess = ScenarioParser.toGraphProcess(process)

    val results = manager.test(ProcessName("test1"), graphProcess,
      TestData.newLineSeparated("""{ "field1": "a", "field2": "b" }"""), identity).futureValue

    val ctxId = IncContextIdGenerator.withProcessIdNodeIdPrefix(process.metaData, "source").nextContextId()
    results.nodeResults("sink") shouldBe List(NodeResult(ResultContext(ctxId, Map("input" -> TypedMap(Map("field1" -> "a", "field2" -> "b"))))))
  }

}
