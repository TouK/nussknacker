package pl.touk.nussknacker.openapi

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance
import pl.touk.nussknacker.engine.api.typed.TypedMap
import pl.touk.nussknacker.engine.util.service.EagerServiceWithErrorSupport.HandleErrorsParamName
import pl.touk.nussknacker.engine.util.service.ServiceResponseWithError
import pl.touk.nussknacker.test.PatientScalaFutures
import sttp.client3.Response
import sttp.client3.testing.SttpBackendStub
import sttp.model.StatusCode

import scala.concurrent.ExecutionContext.Implicits.global

class CodeHandlingTest
    extends AnyFunSuite
    with BeforeAndAfterAll
    with Matchers
    with LazyLogging
    with PatientScalaFutures
    with BaseOpenAPITest {

  private val codeParameter = ParameterName("code")

  private val backend = SttpBackendStub.asynchronousFuture.whenRequestMatchesPartial { case request =>
    val code = request.uri.params.get(codeParameter.value).get.toInt
    Response("{}", StatusCode(code))
  }

  test("should handle configured response codes") {
    // should be non 2xx
    val customEmptyCode = 409
    val config          = baseConfig.copy(codesToInterpretAsEmpty = List(customEmptyCode))

    def runWithCode(code: Int) = {
      val service =
        parseToEnrichers("custom-codes.yml", backend, config, Map(codeParameter -> code))(ServiceName("code"))
      service.invoke(context).futureValue.asInstanceOf[AnyRef]
    }

    runWithCode(customEmptyCode) shouldBe null
    runWithCode(200) shouldBe TypedMap(Map.empty)

    intercept[Exception] {
      runWithCode(404)
    }
    intercept[Exception] {
      runWithCode(503)
    }

  }

  test("should return wrapped response when handleErrors is enabled and request fails") {
    val service = parseToEnrichers(
      "custom-codes.yml",
      backend,
      baseConfig,
      Map(codeParameter -> 503, HandleErrorsParamName -> true)
    )(ServiceName("code"))

    val result = service.invoke(context).futureValue.asInstanceOf[ServiceResponseWithError[AnyRef]]

    result.error shouldBe true
    result.errorResponse should not be empty
    result.errorResponse.get should include("503")
    result.statusCode shouldBe Some(503)
    result.successResponse shouldBe None
  }

  test("should return wrapped success response when handleErrors is enabled") {
    val service = parseToEnrichers(
      "custom-codes.yml",
      backend,
      baseConfig,
      Map(codeParameter -> 200, HandleErrorsParamName -> true)
    )(ServiceName("code"))

    service.invoke(context).futureValue shouldBe ServiceResponseWithError.success(
      TypedMap(Map.empty),
      statusCode = Some(Int.box(200))
    )
  }

}
