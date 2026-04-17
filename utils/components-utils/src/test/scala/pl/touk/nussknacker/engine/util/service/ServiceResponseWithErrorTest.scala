package pl.touk.nussknacker.engine.util.service

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import scala.jdk.CollectionConverters._

class ServiceResponseWithErrorTest extends AnyFunSuite with Matchers {

  test("should encode successful response as json for variable presentation") {
    val response = ServiceResponseWithError.success[AnyRef](
      response = Map("foo" -> "bar").asJava,
      statusCode = Some(Int.box(201))
    )

    response.asJson shouldBe Json.obj(
      "error"           -> Json.fromBoolean(false),
      "errorResponse"   -> Json.Null,
      "successResponse" -> Json.obj("foo" -> Json.fromString("bar")),
      "statusCode"      -> Json.fromInt(201)
    )
  }

  test("should encode error response as json for variable presentation") {
    val response = ServiceResponseWithError.error[AnyRef](
      errorMessage = "service unavailable",
      statusCode = Some(Int.box(503))
    )

    response.asJson shouldBe Json.obj(
      "error"           -> Json.fromBoolean(true),
      "errorResponse"   -> Json.fromString("service unavailable"),
      "successResponse" -> Json.Null,
      "statusCode"      -> Json.fromInt(503)
    )
  }

  test("should fallback to string for unknown success payload type") {
    final class Unknown {
      override def toString: String = "unknown-object"
    }

    val response = ServiceResponseWithError.success[AnyRef](new Unknown)

    response.asJson shouldBe Json.obj(
      "error"           -> Json.fromBoolean(false),
      "errorResponse"   -> Json.Null,
      "successResponse" -> Json.fromString("unknown-object"),
      "statusCode"      -> Json.Null
    )
  }

}
