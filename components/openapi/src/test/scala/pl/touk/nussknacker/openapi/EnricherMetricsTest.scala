package pl.touk.nussknacker.openapi

import cats.data.NonEmptyList
import io.swagger.v3.oas.models.PathItem.HttpMethod
import org.mockito.{ArgumentMatchers, Mockito}
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance
import pl.touk.nussknacker.engine.util.metrics.{MetricIdentifier, MetricsProviderForScenario}
import pl.touk.nussknacker.engine.util.runtimecontext.TestEngineRuntimeContext
import pl.touk.nussknacker.engine.util.service.{AsyncExecutionTimeMeasurement, EspTimer}
import sttp.client3.Response
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.DurationInt

class EnricherMetricsTest extends AnyFunSuite with Matchers with BaseOpenAPITest {

  test("should add correct tag to appropriate swagger services") {
    val backend = SttpBackendStub.asynchronousFuture.whenAnyRequest
      .thenRespondF { _ =>
        Future {
          Response("{}", StatusCode.Ok)
        }
      }
    val metricsProviderForScenario = Mockito.mock[MetricsProviderForScenario]()
    val testEngineRuntimeContext   = TestEngineRuntimeContext(jobData, metricsProviderForScenario)
    val getTimeMeasurement = () => new AsyncExecutionTimeMeasurement(testEngineRuntimeContext, "openAPI", Map.empty)

    val enrichers = parseToEnrichers(
      "multiple-operations.yml",
      backend,
      baseConfig.copy(allowedMethods = List(HttpMethod.GET.name(), HttpMethod.POST.name())),
      getTimeMeasurement = getTimeMeasurement
    )

    enrichers.size shouldBe 2

    enrichers.foreach { case (name, invoker) =>
      Mockito
        .when(metricsProviderForScenario.espTimer(ArgumentMatchers.any(), ArgumentMatchers.any()))
        .thenReturn(EspTimer(() => {}, _ => ()))

      Await.result(invoker.invoke(context), 60 seconds)

      Mockito
        .verify(metricsProviderForScenario, Mockito.times(1))
        .espTimer(
          ArgumentMatchers.eq(MetricIdentifier(NonEmptyList.of("service", "OK"), Map("serviceName" -> name.value))),
          ArgumentMatchers.anyLong
        )
    }
  }

}
