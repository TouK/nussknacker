package pl.touk.nussknacker.test

import io.restassured.specification.RequestSpecification

trait NuRestAssureExtensions {

  implicit class Mocking[T <: RequestSpecification](requestSpecification: T) {

    def assume(f: => Unit): T = {
      val _ = f
      requestSpecification
    }

  }

  implicit class AppConfiguration[T <: RequestSpecification](requestSpecification: T) {

    def applicationConfiguration(f: => Unit): T = {
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

  }

  implicit class JsonBody[T <: RequestSpecification](requestSpecification: T) {

    def jsonBody(json: String): RequestSpecification = {
      requestSpecification
        .contentType("application/json")
        .body(json)
    }

  }

}

object NuRestAssureExtensions extends NuRestAssureExtensions
