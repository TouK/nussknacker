package pl.touk.nussknacker.test

import io.restassured.builder.MultiPartSpecBuilder
import io.restassured.http.ContentType
import io.restassured.response.ValidatableResponse
import io.restassured.specification.RequestSpecification
import org.hamcrest.core.IsEqual
import pl.touk.nussknacker.test.NuRestAssureMatchers.equalsJson

import java.nio.charset.StandardCharsets

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

  implicit class PlainBody[T <: RequestSpecification](requestSpecification: T) {

    def plainBody(body: String): RequestSpecification = {
      requestSpecification
        .contentType(ContentType.TEXT.withCharset(StandardCharsets.UTF_8))
        .body(body)
    }

  }

  implicit class MultiPartBody[T <: RequestSpecification](requestSpecification: T) {
    private val doubleQuote = '"'

    def multiPartBody(fileContent: String, fileName: String): RequestSpecification = {
      requestSpecification
        .multiPart(
          new MultiPartSpecBuilder(fileContent)
            // https://github.com/rest-assured/rest-assured/issues/866#issuecomment-617127889
            .header(
              "Content-Disposition",
              s"form-data; name=${doubleQuote}attachment${doubleQuote}; filename=${doubleQuote}${fileName}${doubleQuote}"
            )
            .build()
        )
        .contentType("multipart/form-data")
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

  implicit class EqualsPlainBody[T <: ValidatableResponse](validatableResponse: T) {

    def equalsPlainBody(body: String): ValidatableResponse = {
      validatableResponse
        .body(
          new IsEqual(body)
        )
    }

  }

  implicit class ExtractLong[T <: ValidatableResponse](validatableResponse: T) {

    def extractLong(jsonPath: String): Long = {
      validatableResponse
        .extract()
        .jsonPath()
        .getLong(jsonPath)
    }

  }

}

object NuRestAssureExtensions extends NuRestAssureExtensions
