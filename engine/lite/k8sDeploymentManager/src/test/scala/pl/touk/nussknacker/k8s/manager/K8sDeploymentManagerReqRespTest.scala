package pl.touk.nussknacker.k8s.manager

import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable, fromMap}
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser
import org.scalatest.OptionValues
import org.scalatest.tags.Network
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentProvider
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.spel.Implicits._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.requirementForName
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import skuber.LabelSelector.dsl._
import skuber.json.format._
import skuber.{ListResource, Service}
import sttp.client.{HttpURLConnectionBackend, Identity, NothingT, SttpBackend, _}

import scala.collection.JavaConverters._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.language.reflectiveCalls

// we use this tag to mark tests using external dependencies
@Network
class K8sDeploymentManagerReqRespTest extends BaseK8sDeploymentManagerTest with OptionValues with EitherValuesDetailedMessage with LazyLogging {

  private lazy val k8sTestUtils = new K8sTestUtils(k8s)
  private implicit val backend: SttpBackend[Identity, Nothing, NothingT] = HttpURLConnectionBackend()

  test("deployment of req-resp ping-pong") {
    val givenScenarioName = "reqresp-ping-pong"
    val givenServicePort = 12345 // some random, remote port, we don't need to worry about collisions
    val f = createReqRespFixture(givenScenarioName, givenServicePort)

    f.withRunningScenario {
      val services = k8s.listSelected[ListResource[Service]](requirementForName(f.version.processName)).futureValue.items

      services should have length 1
      val service = services.head
      service.name shouldEqual givenScenarioName
      val ports = service.spec.value.ports
      ports should have length 1
      ports.head.port shouldEqual givenServicePort

      k8sTestUtils.withForwardedProxyPod(s"http://${service.name}:$givenServicePort") { proxyLocalPort =>
        val pingContent = """Nussknacker!"""
        val pingMessage = s"""{"ping":"$pingContent"}"""
        val instanceIds = (1 to 10).map { _ =>
          val request = basicRequest.post(uri"http://localhost".port(proxyLocalPort).path("scenario", f.scenario.id))
          val jsonResponse = parser.parse(request.body(pingMessage).send().body.rightValue).rightValue
          jsonResponse.hcursor.downField("pong").as[String].rightValue shouldEqual pingContent
          jsonResponse.hcursor.downField("instanceId").as[String].rightValue
        }.toSet

        instanceIds.map(_.length) should contain only (5) // size of k8s instance id hash
        instanceIds.size shouldEqual 2 // default number of replicas
      }
    }
  }

  private def reqRespDeployConfig(port: Int, extraClasses: K8sExtraClasses): Config = {
    val extraClassesVolume = "extra-classes"
    ConfigFactory.empty
      .withValue("mode", fromAnyRef("request-response"))
      .withValue("servicePort", fromAnyRef(port))
      .withValue("k8sDeploymentConfig.spec.template.spec.volumes", fromIterable(List(fromMap(Map(
        "name" -> extraClassesVolume,
        "secret" -> ConfigFactory.parseMap(extraClasses.secretReferenceResourcePart).root()
      ).asJava)).asJava))
      .withValue("k8sDeploymentConfig.spec.template.spec.containers", fromIterable(List(fromMap(Map(
        "name" -> "runtime",
        "volumeMounts" ->
          fromIterable(List(fromMap(Map(
            "name" -> extraClassesVolume,
            "mountPath" -> "/opt/nussknacker/components/common/extra"
          ).asJava)).asJava)
      ).asJava)).asJava))
  }

  private val modelData: LocalModelData = LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator)

  private def createReqRespFixture(givenScenarioName: String, givenServicePort: Int, modelData: LocalModelData = modelData) = {
    val extraClasses = new K8sExtraClasses(k8s,
      List(classOf[TestComponentProvider], classOf[EnvService]),
      K8sExtraClasses.serviceLoaderConfigURL(getClass, classOf[ComponentProvider]))
    val deployConfig = reqRespDeployConfig(givenServicePort, extraClasses)
    val manager = K8sDeploymentManager(modelData, deployConfig)
    val pingSchema = """{
                       |  "type": "object",
                       |  "properties": {
                       |    "ping": { "type": "string" }
                       |  }
                       |}
                       |""".stripMargin

    val pongSchema = """{
                       |  "type": "object",
                       |  "properties": {
                       |    "pong": { "type": "string" },
                       |    "instanceId": { "type": "string" }
                       |  }
                       |}
                       |""".stripMargin
    val scenario = ScenarioBuilder
      .requestResponse(givenScenarioName)
      .additionalFields(properties = Map(
        "inputSchema" -> pingSchema,
        "outputSchema" -> pongSchema
      ))
      .source("source", "request")
      .enricher("instanceId", "instanceId", "env", "name" -> "\"INSTANCE_ID\"")
      .emptySink("sink", "response", "pong" -> "#input.ping", "instanceId" -> "#instanceId")
    logger.info(s"Running req-resp test on ${scenario.id}")
    val version = ProcessVersion(VersionId(11), ProcessName(scenario.id), ProcessId(1234), "testUser", Some(22))
    new ReqRespTestFixture(manager = manager, scenario = scenario, version = version, extraClasses)
  }

  private class ReqRespTestFixture(manager: K8sDeploymentManager,
                                   scenario: EspProcess,
                                   version: ProcessVersion,
                                   extraClasses: K8sExtraClasses) extends K8sDeploymentManagerTestFixture(manager, scenario, version) {
    override def withRunningScenario(action: => Unit): Unit = {
      extraClasses.withExtraClassesSecret {
        super.withRunningScenario(action)
        //should not fail
        assertNoGarbageLeft()
      }
    }

    override protected def onException(ex: Throwable): Unit = {
      k8sTestUtils.clusterInfoDump()
    }
  }

}
