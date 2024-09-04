package pl.touk.nussknacker.test

import io.restassured.http.ContentType
import io.restassured.response.ValidatableResponse
import io.restassured.specification.RequestSpecification
import org.hamcrest.core.IsEqual
import pl.touk.nussknacker.test.NuRestAssureMatchers.equalsJson

import java.nio.charset.StandardCharsets
import scala.jdk.CollectionConverters._

trait NuRestAssureExtensions {

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

    // https://github.com/rest-assured/rest-assured/issues/507
    def preemptiveBasicAuth(name: String, password: String): RequestSpecification = {
      requestSpecification
        .auth()
        .preemptive()
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

  implicit class PlainBody[T <: RequestSpecification](requestSpecification: T) {

    def plainBody(body: String): RequestSpecification = {
      requestSpecification
        .contentType(ContentType.TEXT.withCharset(StandardCharsets.UTF_8))
        .body(body)
    }

  }

  implicit class StreamBody[T <: RequestSpecification](requestSpecification: T) {
    private val doubleQuote = '"'

    def streamBody(fileContent: String, fileName: String): RequestSpecification = {
      requestSpecification
        .body(fileContent.getBytes(StandardCharsets.UTF_8))
        .contentType(ContentType.BINARY)
        .header(
          "Content-Disposition",
          s"attachment; filename=${doubleQuote}${fileName}${doubleQuote}"
        )
    }

  }

  implicit class VerifyState(validatableResponse: ValidatableResponse) {

    def verifyApplicationState(f: => Unit): ValidatableResponse = {
      val _ = f
      validatableResponse
    }

    def verifyExternalState(f: => Unit): ValidatableResponse = {
      val _ = f
      validatableResponse
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

  implicit class ExtractUsingJsonPath[T <: ValidatableResponse](validatableResponse: T) {

    def extractLong(jsonPath: String): Long = {
      validatableResponse
        .extract()
        .jsonPath()
        .getLong(jsonPath)
    }

    def extractString(jsonPath: String): String = {
      validatableResponse
        .extract()
        .jsonPath()
        .getString(jsonPath)
    }

    def extractList(jsonPath: String): List[String] = {
      validatableResponse
        .extract()
        .jsonPath()
        .getList[String](jsonPath)
        .asScala
        .toList
    }

  }

}

object NuRestAssureExtensions extends NuRestAssureExtensions
