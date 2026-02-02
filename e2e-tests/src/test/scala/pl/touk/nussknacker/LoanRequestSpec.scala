package pl.touk.nussknacker

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import org.scalatest.tags.Slow
import pl.touk.nussknacker.test.VeryPatientScalaFutures
import pl.touk.nussknacker.test.installationexample.HttpResponse

@Slow
class LoanRequestSpec extends AnyFreeSpecLike with BaseE2ESpec with Matchers with VeryPatientScalaFutures {

  "Properly handle loan request" in {
    val result = client.sendHttpRequest(
      serviceSlug = "loan",
      payload = ujson.read {
        """{
          |  "customerId": "anon",
          |  "requestedAmount": 1555,
          |  "requestType": "mortgage",
          |  "location": { "city": "Warszawa", "street": "Marszałkowska" }
          |}""".stripMargin
      }
    )
    result should be(
      Right(HttpResponse(200, ujson.read("""{"acceptedAmount":1000,"message":"Large sum for Warszawa"}""")))
    )
  }

}
