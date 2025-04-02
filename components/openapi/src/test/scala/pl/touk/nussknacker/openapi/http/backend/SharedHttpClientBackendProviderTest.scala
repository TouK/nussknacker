package pl.touk.nussknacker.openapi.http.backend

import org.asynchttpclient.filter.FilterException
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{JobData, MetaData, ProcessAdditionalFields, ProcessVersion}
import pl.touk.nussknacker.engine.api.runtimecontext.{ContextIdGenerator, EngineRuntimeContext}
import pl.touk.nussknacker.engine.util.metrics.MetricsProviderForScenario
import pl.touk.nussknacker.http.backend.DefaultHttpClientConfig
import sttp.client3.basicRequest
import sttp.model.Uri

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class SharedHttpClientBackendProviderTest extends AnyFunSuite with Matchers {

  test("should throw exception on forbidden URL") {
    val httpConfig      = DefaultHttpClientConfig().copy(forbiddenHosts = Some(List("localhost")))
    val backendProvider = new SharedHttpClientBackendProvider(httpConfig)
    backendProvider.open(new EngineRuntimeContext {
      override def jobData: JobData = JobData(
        MetaData("id", ProcessAdditionalFields(None, Map.empty, "type")),
        ProcessVersion.empty
      )
      override def metricsProvider: MetricsProviderForScenario            = ???
      override def contextIdGenerator(nodeId: String): ContextIdGenerator = ???
    })

    val responseFuture = backendProvider.httpBackendForEc
      .send(basicRequest.get(Uri.unsafeParse("http://localhost:8080/abc")))

    intercept[FilterException] {
      Await.result(responseFuture, 5 seconds)
    }

    backendProvider.close()
  }

}
