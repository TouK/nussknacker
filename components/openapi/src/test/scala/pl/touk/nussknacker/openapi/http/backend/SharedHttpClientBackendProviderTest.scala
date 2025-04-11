package pl.touk.nussknacker.openapi.http.backend

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock.{get, permanentRedirect, urlEqualTo}
import com.github.tomakehurst.wiremock.core.WireMockConfiguration
import org.asynchttpclient.filter.FilterException
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessAdditionalFields, ProcessVersion}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario
import pl.touk.nussknacker.http.backend.DefaultHttpClientConfig
import pl.touk.nussknacker.test.AvailablePortFinder
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

  private val wireMock: WireMockServer = {
    val server = AvailablePortFinder.withAvailablePortsBlocked(1)(l => {
      new WireMockServer(
        WireMockConfiguration
          .wireMockConfig()
          .port(l.head)
      )
    })
    server.start()
    server
  }

  override protected def afterAll(): Unit = {
    try {
      wireMock.stop()
    } finally {
      super.afterAll()
    }
  }

  private val engineRuntimeContext = new EngineRuntimeContext {
    override def jobData: JobData = JobData(
      MetaData("id", ProcessAdditionalFields(None, Map.empty, "type")),
      ProcessVersion.empty
    )
    override def metricsProvider: MetricsProviderForScenario            = ???
    override def contextIdGenerator(nodeId: String): ContextIdGenerator = ???
  }

  test("should throw exception on forbidden URL") {
    val httpConfig = DefaultHttpClientConfig().copy(
      isLocalhostAllowed = Some(false),
      forbiddenHostRegexes = Some(List("namespace.*svc.cluster.local")),
    )
    val backendProvider = new SharedHttpClientBackendProvider(httpConfig)
    backendProvider.open(engineRuntimeContext)

    forAll(
      Table(
        "http://namespace.nussknacker.svc.cluster.local/abc",
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

  test("should throw exception on forbidden URL for redirect") {
    val httpConfig = DefaultHttpClientConfig().copy(
      followRedirect = Some(true),
      isLocalhostAllowed = Some(true),
      forbiddenHostRegexes = Some(List("namespace.*svc.cluster.local")),
    )
    val backendProvider = new SharedHttpClientBackendProvider(httpConfig)
    backendProvider.open(engineRuntimeContext)

    wireMock.stubFor(
      get(urlEqualTo("/abc")).willReturn(
        permanentRedirect("http://namespace.nussknacker.svc.cluster.local/abc")
      )
    )

    val responseFuture = backendProvider.httpBackendForEc
      .send(basicRequest.get(Uri.unsafeParse(s"${wireMock.baseUrl()}/abc")))

    intercept[FilterException] {
      Await.result(responseFuture, 5 seconds)
    }

    backendProvider.close()
  }

}
