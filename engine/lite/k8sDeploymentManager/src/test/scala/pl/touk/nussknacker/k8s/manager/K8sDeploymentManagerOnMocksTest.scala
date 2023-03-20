package pl.touk.nussknacker.k8s.manager

import akka.actor.ActorSystem
import akka.stream.scaladsl.TcpIdleTimeoutException
import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock._
import com.typesafe.config.{Config, ConfigFactory}
import org.scalatest.concurrent.PatienceConfiguration.Timeout
import org.scalatest.exceptions.TestFailedException
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.time.Span.convertSpanToDuration
import org.scalatest.{BeforeAndAfterAll, Inside, OptionValues}
import pl.touk.nussknacker.engine.api.process.{EmptyProcessConfigCreator, ProcessName}
import pl.touk.nussknacker.engine.testing.LocalModelData
import pl.touk.nussknacker.test.{AvailablePortFinder, PatientScalaFutures}
import skuber.api.Configuration
import skuber.api.client.KubernetesClient

import scala.concurrent.duration._
import scala.util.{Failure, Try}

class K8sDeploymentManagerOnMocksTest extends AnyFunSuite with BeforeAndAfterAll with PatientScalaFutures
  with Inside with Matchers with OptionValues {

  private val clientIdleTimeout = 1.second

  // Here we've got a trouble. The only way to configure skuber's http client is to provide configuration
  // on the root level (in our case on designer root config level). See KubernetesClientImpl.invoke for details.
  // It is not acceptable for us, because we want to have configuration separation for each DM.
  // TODO: Figure out how to resolve this problem. Maybe we need to fork skuber or change the client?
  protected implicit val system: ActorSystem = ActorSystem(getClass.getSimpleName, ConfigFactory.parseString(
    s"""akka.http.client {
       |  idle-timeout: ${clientIdleTimeout.toMillis} ms
       |}""".stripMargin))
  import system.dispatcher

  private var wireMockServer: WireMockServer = _

  test("return process state respecting a short timeout for this operation") {
    def stubWithFixedDelay(delay: FiniteDuration): Unit = {
      wireMockServer.stubFor(
        get(urlPathEqualTo("/apis/apps/v1/namespaces/default/deployments")).willReturn(
          aResponse()
            .withBody(
              """{
                |  "apiVersion": "v1",
                |  "kind": "DeploymentList",
                |  "items": []
                |}""".stripMargin)
            .withHeader("Content-Type", "application/json")
            .withFixedDelay(delay.toMillis.toInt)))
      wireMockServer.stubFor(
        get(urlPathEqualTo("/api/v1/namespaces/default/pods")).willReturn(
          aResponse()
            .withBody(
              """{
                |  "apiVersion": "v1",
                |  "kind": "PodList",
                |  "items": []
                |}""".stripMargin)
            .withHeader("Content-Type", "application/json")))
    }
    val manager = new K8sDeploymentManager(LocalModelData(ConfigFactory.empty, new EmptyProcessConfigCreator()), K8sDeploymentManagerConfig(), ConfigFactory.empty()) {
      override protected def createK8sClient(skuberAppConfig: Config): KubernetesClient = {
        skuber.k8sInit(Configuration.useLocalProxyOnPort(wireMockServer.port()), skuberAppConfig)
      }
    }

    val durationLongerThanExpectedClientTimeout = clientIdleTimeout.plus(convertSpanToDuration(patienceConfig.timeout))
    stubWithFixedDelay(durationLongerThanExpectedClientTimeout)
    inside(Try(manager.getFreshProcessState(ProcessName("foo")).futureValue(Timeout(durationLongerThanExpectedClientTimeout.plus(100 millis))))) {
      case Failure(ex: TestFailedException) =>
        Option(ex.getCause).value shouldBe a[TcpIdleTimeoutException]
    }

    stubWithFixedDelay(0 seconds)
    val result = manager.getFreshProcessState(ProcessName("foo")).futureValue(Timeout(durationLongerThanExpectedClientTimeout.plus(100 millis)))
    result shouldEqual None
  }

  override protected def beforeAll(): Unit = {
    wireMockServer = AvailablePortFinder.withAvailablePortsBlocked(1)(l => new WireMockServer(l.head))
    wireMockServer.start()
  }

  override protected def afterAll(): Unit = {
    system.terminate().futureValue
    wireMockServer.stop()
  }
}
