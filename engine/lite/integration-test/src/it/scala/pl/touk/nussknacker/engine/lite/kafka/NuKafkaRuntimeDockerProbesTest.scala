package pl.touk.nussknacker.engine.lite.kafka

import com.dimafeng.testcontainers._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.lite.kafka.sample.NuKafkaRuntimeTestSamples
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.kafka.UnspecializedTopicName.ToUnspecializedTopicName
import pl.touk.nussknacker.engine.schemedkafka.schemaregistry.confluent.ConfluentUtils
import pl.touk.nussknacker.test.{EitherValuesDetailedMessage, ExtremelyPatientScalaFutures}
import sttp.client3.asynchttpclient.future.AsyncHttpClientFutureBackend
import sttp.client3.{SttpBackend, UriContext, asString, basicRequest}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class NuKafkaRuntimeDockerProbesTest
    extends AnyFunSuite
    with BaseNuKafkaRuntimeDockerTest
    with Matchers
    with ExtremelyPatientScalaFutures
    with LazyLogging
    with EitherValuesDetailedMessage {

  override val container: Container = {
    kafkaContainer.start()          // must be started before prepareTestCaseFixture because it creates topic via api
    schemaRegistryContainer.start() // should be started after kafka
    fixture =
      prepareTestCaseFixture(NuKafkaRuntimeTestSamples.pingPongScenarioName, NuKafkaRuntimeTestSamples.pingPongScenario)
    registerSchemas()
    startRuntimeContainer(fixture.scenarioFile, checkReady = false)
    MultipleContainers(kafkaContainer, schemaRegistryContainer, runtimeContainer)
  }

  private def registerSchemas(): Unit = {
    schemaRegistryClient.register(
      ConfluentUtils.valueSubject(fixture.inputTopic.toUnspecialized),
      NuKafkaRuntimeTestSamples.jsonPingSchema
    )
    schemaRegistryClient.register(
      ConfluentUtils.valueSubject(fixture.outputTopic.toUnspecialized),
      NuKafkaRuntimeTestSamples.jsonPingSchema
    )
  }

  private implicit val backend: SttpBackend[Future, Any] = AsyncHttpClientFutureBackend()
  private val baseManagementUrl                          = uri"http://localhost:$mappedRuntimeApiPort"

  test("readiness probe") {
    eventually {
      val readyResult = basicRequest
        .get(baseManagementUrl.withPath("ready"))
        .response(asString)
        .send(backend)
        .futureValue
        .body
        .rightValue
      readyResult shouldBe "OK"
    }
  }

  test("liveness probe") {
    eventually {
      val livenessResult = basicRequest
        .get(baseManagementUrl.withPath("alive"))
        .response(asString)
        .send(backend)
        .futureValue
        .body
        .rightValue
      livenessResult shouldBe "OK"
    }
  }

}
