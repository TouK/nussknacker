package pl.touk.nussknacker.k8s.manager

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.config.ConfigValueFactory.{fromAnyRef, fromIterable, fromMap}
import com.typesafe.scalalogging.LazyLogging
import io.circe.parser
import org.scalatest.OptionValues
import org.scalatest.concurrent.PatienceConfiguration
import org.scalatest.tags.Network
import org.scalatest.time.{Seconds, Span}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.component.ComponentProvider
import pl.touk.nussknacker.engine.api.deployment.{
  DataFreshnessPolicy,
  DeploymentUpdateStrategy,
  DMRunDeploymentCommand,
  DMValidateScenarioCommand
}
import pl.touk.nussknacker.engine.api.deployment.DeploymentUpdateStrategy.StateRestoringStrategy
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.build.ScenarioBuilder
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.DeploymentData
import pl.touk.nussknacker.engine.spel.SpelExtension._
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.k8s.manager.K8sDeploymentManager.requirementForName
import pl.touk.nussknacker.test.EitherValuesDetailedMessage
import skuber.{LabelSelector, ListResource, Service}
import skuber.LabelSelector.dsl._
import skuber.json.format._
import skuber.networking.v1.Ingress
import sttp.client3._

import scala.concurrent.ExecutionContext.Implicits._
import scala.jdk.CollectionConverters._
import scala.language.reflectiveCalls
import scala.util.Random

// we use this tag to mark tests using external dependencies
@Network
class K8sDeploymentManagerReqRespTest
    extends BaseK8sDeploymentManagerTest
    with OptionValues
    with EitherValuesDetailedMessage
    with LazyLogging {

  private implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
  private val givenServicePort = 12345 // some random, remote port, we don't need to worry about collisions

  test("deployment of req-resp ping-pong") {
    val givenScenarioName = "reqresp-ping-pong"
    val f                 = createReqRespFixture(givenScenarioName)

    f.withRunningScenario {
      val services =
        k8s.listSelected[ListResource[Service]](requirementForName(f.version.processName)).futureValue.items

      services should have length 1
      val service = services.head
      service.name shouldEqual givenScenarioName
      val ports = service.spec.value.ports
      ports should have length 1
      ports.head.port shouldEqual givenServicePort

      k8sTestUtils.withForwardedProxyPod(s"http://${service.name}:$givenServicePort") { proxyLocalPort =>
        val pingContent = """Nussknacker!"""
        val pingMessage = s"""{"ping":"$pingContent"}"""
        var instanceIds = Set.empty[String]
        eventually {
          val request      = basicRequest.post(uri"http://localhost".port(proxyLocalPort))
          val response     = request.body(pingMessage).send(backend).futureValue.body.rightValue
          val jsonResponse = parser.parse(response).rightValue
          jsonResponse.hcursor.downField("pong").as[String].rightValue shouldEqual pingContent
          instanceIds += jsonResponse.hcursor.downField("instanceId").as[String].rightValue
          instanceIds should have size 2 // default number of replicas
        }

        instanceIds.map(_.length) should contain only (5) // size of k8s instance id hash
      }
    }
  }

  test("deployment of req-resp with ingress") {
    val givenScenarioName = "reqresp-ingress"
    val config            = ConfigFactory.empty().withValue("ingress.enabled", fromAnyRef(true))
    val f                 = createReqRespFixture(givenScenarioName, extraDeployConfig = config)

    f.withRunningScenario {
      k8s
        .listSelected[ListResource[Ingress]](requirementForName(f.version.processName))
        .futureValue
        .items
        .headOption shouldBe Symbol("defined")

      val pingContent = """Nussknacker!"""
      val pingMessage = s"""{"ping":"$pingContent"}"""
      val request     = basicRequest.post(uri"http://localhost".port(8081).withPath(givenScenarioName))
      val response =
        eventually(PatienceConfiguration.Timeout(Span(10, Seconds))) { // nginx returns 503 even if service is ready
          request.body(pingMessage).send(backend).futureValue.body.rightValue
        }
      val jsonResponse = parser.parse(response).rightValue
      jsonResponse.hcursor.downField("pong").as[String].rightValue shouldEqual pingContent
    }
  }

  test("deployment of secured req-resp") {
    val givenScenarioName = "reqresp-secured"
    val config = ConfigFactory
      .empty()
      .withValue("ingress.enabled", fromAnyRef(true))
      .withValue("configExecutionOverrides.request-response.security.basicAuth.user", fromAnyRef("publisher"))
      .withValue("configExecutionOverrides.request-response.security.basicAuth.password", fromAnyRef("rrPassword"))
    val f = createReqRespFixture(givenScenarioName, extraDeployConfig = config)

    f.withRunningScenario {
      k8s
        .listSelected[ListResource[Ingress]](requirementForName(f.version.processName))
        .futureValue
        .items
        .headOption shouldBe Symbol("defined")

      val pingContent = """Nussknacker!"""
      val pingMessage = s"""{"ping":"$pingContent"}"""
      val request = basicRequest.auth
        .basic("publisher", "rrPassword")
        .post(uri"http://localhost".port(8081).withPath(givenScenarioName))
      val response =
        eventually(PatienceConfiguration.Timeout(Span(10, Seconds))) { // nginx returns 503 even if service is ready
          request.body(pingMessage).send(backend).futureValue.body.rightValue
        }
      val jsonResponse = parser.parse(response).rightValue
      jsonResponse.hcursor.downField("pong").as[String].rightValue shouldEqual pingContent
    }
  }

  test("redeployment of req-resp") {
    val givenScenarioName = "reqresp-redeploy"
    val firstVersion      = 1
    val f = createReqRespFixture(
      givenScenarioName,
      firstVersion,
      extraDeployConfig = ConfigFactory.empty().withValue("scalingConfig.fixedReplicasCount", fromAnyRef(2))
    )

    f.withRunningScenario {
      k8sTestUtils.withForwardedProxyPod(s"http://$givenScenarioName:$givenServicePort") { proxyLocalPort =>
        val pingContent = """Nussknacker!"""
        val pingMessage = s"""{"ping":"$pingContent"}"""

        def checkVersions() = (1 to 10).map { _ =>
          val request = basicRequest.post(uri"http://localhost".port(proxyLocalPort))
          val response =
            eventually(PatienceConfiguration.Timeout(Span(10, Seconds))) { // nginx returns 503 even if service is ready
              request.body(pingMessage).send(backend).futureValue.body.rightValue
            }
          val jsonResponse = parser.parse(response).rightValue
          jsonResponse.hcursor.downField("version").as[Int].rightValue
        }.toSet

        val versionsBeforeRedeploy = checkVersions()
        versionsBeforeRedeploy shouldEqual Set(firstVersion)

        val secondVersion     = 2
        val secondVersionInfo = f.version.copy(versionId = VersionId(secondVersion))
        // It can take a while on CI :/
        f.manager
          .processCommand(
            DMRunDeploymentCommand(
              secondVersionInfo,
              DeploymentData.empty,
              preparePingPongScenario(givenScenarioName, secondVersion),
              DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
                StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
              )
            )
          )
          .futureValue
        eventually {
          val state = f.manager.getScenarioDeploymentsStatuses(secondVersionInfo.processName).map(_.value).futureValue
          state.flatMap(_.version).map(_.value) shouldBe List(secondVersion)
          state.map(_.status) shouldBe List(SimpleStateStatus.Running)
        }
        val versionsAfterRedeploy = checkVersions()
        versionsAfterRedeploy shouldEqual Set(secondVersion)
      }
    }
  }

  test("check slug uniqueness validation") {
    val givenScenarioName = "reqresp-uniqueness"
    val slug              = "slug1"
    val f                 = createReqRespFixture(givenScenarioName, givenSlug = Some(slug))

    f.withRunningScenario {
      // ends without errors, we only change version
      f.manager
        .processCommand(
          DMValidateScenarioCommand(
            f.version.copy(versionId = VersionId(2)),
            DeploymentData.empty,
            f.scenario,
            DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            )
          )
        )
        .futureValue

      val newName = "reqresp-other"
      // different id and name
      val newVersion = f.version.copy(
        processName = ProcessName(newName),
        processId = ProcessId(Random.nextInt(1000)),
        versionId = VersionId(2)
      )
      val scenario = preparePingPongScenario(newName, 2, Some(slug))

      val failure =
        f.manager
          .processCommand(
            DMValidateScenarioCommand(
              newVersion,
              DeploymentData.empty,
              scenario,
              DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
                StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
              )
            )
          )
          .failed
          .futureValue
      failure.getMessage shouldBe s"Slug is not unique, scenario $givenScenarioName is using it"
    }

  }

  test("check cleanup after slug change") {
    val givenScenarioName = "reqresp-slugchange"
    val slug              = "slug1"
    val f                 = createReqRespFixture(givenScenarioName, givenSlug = Some(slug))

    f.withRunningScenario {
      val otherSlug   = "otherSlug"
      val changedSlug = preparePingPongScenario(givenScenarioName, 1, Some(otherSlug))
      val newVersion  = f.version.copy(versionId = VersionId(Random.nextInt(1000)))
      f.manager
        .processCommand(
          DMRunDeploymentCommand(
            newVersion,
            DeploymentData.empty,
            changedSlug,
            DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
              StateRestoringStrategy.RestoreStateFromReplacedJobSavepoint
            )
          )
        )
        .futureValue
      f.waitForRunning(newVersion)
      val servicesForScenario = k8s
        .listSelected[ListResource[Service]](LabelSelector(requirementForName(newVersion.processName)))
        .futureValue
        .items
      servicesForScenario.map(_.name) should have length 1
    }

  }

  private def reqRespDeployConfig(
      port: Int,
      extraClasses: K8sExtraClasses,
      fallback: Config
  ): Config = {
    val extraClassesVolume = "extra-classes"
    val runtimeContainerConfig = baseRuntimeContainerConfig
      .withValue(
        "volumeMounts",
        fromIterable(
          List(
            fromMap(
              Map(
                "name"      -> extraClassesVolume,
                "mountPath" -> "/opt/nussknacker/components/common/extra"
              ).asJava
            )
          ).asJava
        )
      )
      .root()
    baseDeployConfig("request-response")
      .withValue("servicePort", fromAnyRef(port))
      .withValue(
        "k8sDeploymentConfig.spec.template.spec.volumes",
        fromIterable(
          List(
            fromMap(
              Map(
                "name"   -> extraClassesVolume,
                "secret" -> ConfigFactory.parseMap(extraClasses.secretReferenceResourcePart).root()
              ).asJava
            )
          ).asJava
        )
      )
      .withValue("k8sDeploymentConfig.spec.template.spec.containers", fromIterable(List(runtimeContainerConfig).asJava))
      .withFallback(fallback)
  }

  private val modelData: LocalModelData = LocalModelData(ConfigFactory.empty, List.empty)

  private def createReqRespFixture(
      givenScenarioName: String,
      givenVersion: Int = 1,
      givenSlug: Option[String] = None,
      modelData: ModelData = modelData,
      extraDeployConfig: Config = ConfigFactory.empty()
  ) = {
    val extraClasses = new K8sExtraClasses(
      k8s,
      List(classOf[TestComponentProvider], classOf[EnvService]),
      K8sExtraClasses.serviceLoaderConfigURL(getClass, classOf[ComponentProvider])
    )
    val deployConfig = reqRespDeployConfig(givenServicePort, extraClasses, extraDeployConfig)
    val manager      = prepareManager(modelData, deployConfig)
    val scenario     = preparePingPongScenario(givenScenarioName, givenVersion, givenSlug)
    logger.info(s"Running req-resp test on ${scenario.name}")
    val version =
      ProcessVersion(VersionId(givenVersion), scenario.name, ProcessId(1234), List.empty, "testUser", Some(22))
    new ReqRespTestFixture(manager = manager, scenario = scenario, version = version, extraClasses)
  }

  private def preparePingPongScenario(scenarioName: String, version: Int, slug: Option[String] = None) = {
    val pingSchema =
      """{
        |  "type": "object",
        |  "properties": {
        |    "ping": { "type": "string" }
        |  }
        |}
        |""".stripMargin

    val pongSchema =
      """{
        |  "type": "object",
        |  "properties": {
        |    "pong": { "type": "string" },
        |    "instanceId": { "type": "string" },
        |    "version": { "type": "integer" }
        |  }
        |}
        |""".stripMargin
    ScenarioBuilder
      .requestResponse(scenarioName)
      .slug(slug)
      .additionalFields(properties =
        Map(
          "inputSchema"  -> pingSchema,
          "outputSchema" -> pongSchema
        )
      )
      .source("source", "request")
      .enricher("instanceId", "instanceId", "env", "name" -> "\"INSTANCE_ID\"".spel)
      .emptySink(
        "sink",
        "response",
        "Raw editor" -> "false".spel,
        "pong"       -> "#input.ping".spel,
        "instanceId" -> "#instanceId".spel,
        "version"    -> version.toString.spel
      )
  }

  private class ReqRespTestFixture(
      manager: K8sDeploymentManager,
      scenario: CanonicalProcess,
      version: ProcessVersion,
      extraClasses: K8sExtraClasses
  ) extends K8sDeploymentManagerTestFixture(manager, scenario, version) {

    override def withRunningScenario(action: => Unit): Unit = {
      extraClasses.withExtraClassesSecret {
        super.withRunningScenario(action)
        // should not fail
        assertNoGarbageLeft()
      }
    }

  }

}
