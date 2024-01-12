package pl.touk.nussknacker.test

import io.restassured.response.ValidatableResponse
import io.restassured.specification.RequestSpecification
import pl.touk.nussknacker.test.NuRestAssureMatchers.equalsJson

trait NuRestAssureExtensions {

  implicit class Mocking[T <: RequestSpecification](requestSpecification: T) {

    def assume(f: => Unit): T = {
      val _ = f
      requestSpecification
    }

  }

  implicit class AppConfiguration[T <: RequestSpecification](requestSpecification: T) {

    def applicationState(f: => Unit): T = {
      val _ = f
      requestSpecification
    }

  }

  implicit class BasicAuth[T <: RequestSpecification](requestSpecification: T) {

    def basicAuth(name: String, password: String): RequestSpecification = {
      requestSpecification
        .auth()
        .basic(name, password)
    }

    def noAuth(): RequestSpecification = {
      requestSpecification
        .auth()
        .none()
    }

  }

  implicit class JsonBody[T <: RequestSpecification](requestSpecification: T) {

    def jsonBody(json: String): RequestSpecification = {
      requestSpecification
        .contentType("application/json")
        .body(json)
    }

  }

  implicit class EqualsJsonBody[T <: ValidatableResponse](validatableResponse: T) {

    def equalsJsonBody(json: String): ValidatableResponse = {
      validatableResponse
        .body(
          equalsJson(json)
        )
    }

  }

}

object NuRestAssureExtensions extends NuRestAssureExtensions
