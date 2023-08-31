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
}
object NuRestAssureExtensions extends NuRestAssureExtensions