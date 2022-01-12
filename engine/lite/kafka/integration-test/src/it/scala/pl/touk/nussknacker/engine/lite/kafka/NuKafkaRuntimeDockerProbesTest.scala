package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, ExtremelyPatientScalaFutures}
import sttp.client.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client.{NothingT, SttpBackend, UriContext, asString, basicRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NuKafkaRuntimeDockerProbesTest extends FunSuite with BaseNuKafkaRuntimeDockerTest with Matchers with ExtremelyPatientScalaFutures with LazyLogging with EitherValuesDetailedMessage {

  override val container: Container = {
    kafkaContainer.start() // must be started before prepareTestCaseFixture because it creates topic via api
    fixture = prepareTestCaseFixture("probes", NuKafkaRuntimeTestSamples.jsonPingPongScenario)
    startRuntimeContainer(fixture.scenarioFile, checkReady = false)
    MultipleContainers(kafkaContainer, runtimeContainer)
  }

  private implicit val backend: SttpBackend[Future, Nothing, NothingT] = AsyncHttpClientFutureBackend()
  private val baseManagementUrl = uri"http://localhost:$runtimeManagementMappedPort"

  test("readiness probe") {
    eventually {
      val readyResult = basicRequest
        .get(baseManagementUrl.path("ready"))
        .response(asString)
        .send().futureValue.body.rightValue
      readyResult shouldBe "OK"
    }
  }

  test("liveness probe") {
    eventually {
      val livenessResult = basicRequest
        .get(baseManagementUrl.path("alive"))
        .response(asString)
        .send().futureValue.body.rightValue
      livenessResult shouldBe "OK"
    }
  }

}