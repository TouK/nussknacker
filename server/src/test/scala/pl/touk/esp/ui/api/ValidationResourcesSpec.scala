package pl.touk.esp.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._

class ValidationResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside with ProcessPosting {

  val route = new ValidationResources(ValidationTestData.validator).route

  it should "find errors in a bad process" in {
    Post("/processValidation", toEntity(ValidationTestData.invalidProcess)) ~> route ~> check {
      status shouldEqual StatusCodes.BadRequest
      val entity = entityAs[String]
      entity should include ("MissingSourceFactory")
    }
  }

  it should "find no errors in a good process" in {
    Post("/processValidation", toEntity(ValidationTestData.validProcess)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

}
