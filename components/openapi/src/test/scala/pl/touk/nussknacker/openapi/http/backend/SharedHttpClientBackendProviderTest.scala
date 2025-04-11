package pl.touk.nussknacker.openapi.http.backend

import org.asynchttpclient.filter.FilterException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessAdditionalFields, ProcessVersion}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario
import pl.touk.nussknacker.http.backend.DefaultHttpClientConfig
import sttp.client3.basicRequest
import sttp.model.Uri

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class SharedHttpClientBackendProviderTest
    extends AnyFunSuite
    with Matchers
    with TableDrivenPropertyChecks
    with BeforeAndAfterAll {

  private val engineRuntimeContext = new EngineRuntimeContext {
    override def jobData: JobData = JobData(
      MetaData("id", ProcessAdditionalFields(None, Map.empty, "type")),
      ProcessVersion.empty
    )
    override def metricsProvider: MetricsProviderForScenario            = ???
    override def contextIdGenerator(nodeId: String): ContextIdGenerator = ???
  }

  test("should throw exception when called address is localhost and localhost is not allowd") {
    val httpConfig = DefaultHttpClientConfig().copy(
      isLocalhostAllowed = Some(false),
    )
    val backendProvider = new SharedHttpClientBackendProvider(httpConfig)
    backendProvider.open(engineRuntimeContext)

    forAll(
      Table(
        "http://0.0.0.0",
        "http://127.0.0.1",
        "http://127.0.0.1.nip.io",
        "http://127.0.190.1",
      )
    ) { host =>
      val responseFuture = backendProvider.httpBackendForEc
        .send(basicRequest.get(Uri.unsafeParse(host)))

      intercept[FilterException] {
        Await.result(responseFuture, 5 seconds)
      }
    }

    backendProvider.close()
  }

  test("should throw exception on forbidden URL") {
    val httpConfig = DefaultHttpClientConfig().copy(
      isLocalhostAllowed = Some(true),
      forbiddenHosts = Some(List("localhost")),
    )
    val backendProvider = new SharedHttpClientBackendProvider(httpConfig)
    backendProvider.open(engineRuntimeContext)

    val responseFuture = backendProvider.httpBackendForEc
      .send(basicRequest.get(Uri.unsafeParse("http://localhost/abc")))

    intercept[FilterException] {
      Await.result(responseFuture, 5 seconds)
    }

    backendProvider.close()
  }

}
