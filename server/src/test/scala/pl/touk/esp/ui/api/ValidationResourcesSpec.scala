package pl.touk.esp.ui.api

import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.testkit.ScalatestRouteTest
import org.scalatest._
import pl.touk.esp.ui.api.helpers.TestFactory._
import pl.touk.esp.ui.security.{LoggedUser, Permission}

class ValidationResourcesSpec extends FlatSpec with ScalatestRouteTest with Matchers with Inside {

  val route = withPermissions(new ValidationResources(processValidation, processConverter).route, Permission.Read)

  it should "find errors in a bad process" in {
    Post("/processValidation", posting.toEntity(ProcessTestData.invalidProcess)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
      val entity = entityAs[String]
      entity should include ("MissingSourceFactory")
    }
  }

  it should "find no errors in a good process" in {
    Post("/processValidation", posting.toEntity(ProcessTestData.validProcess)) ~> route ~> check {
      status shouldEqual StatusCodes.OK
    }
  }

}
