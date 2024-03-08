package pl.touk.nussknacker.openapi

import com.typesafe.scalalogging.LazyLogging
import org.scalatest.BeforeAndAfterAll
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.ContextId
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.test.EmptyInvocationCollector.Instance
import pl.touk.nussknacker.engine.api.typed.TypedMap
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
    val service         = parseToEnrichers("custom-codes.yml", backend, config)(ServiceName("code"))

    def runWithCode(code: Int) = {
      implicit val contextId: ContextId = ContextId("1")
      service.invoke(Map(codeParameter -> code)).futureValue.asInstanceOf[AnyRef]
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

}
