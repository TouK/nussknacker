package pl.touk.nussknacker.config

import io.restassured.specification.RequestSpecification
import pl.touk.nussknacker.test.NuRestAssureExtensions

trait WithE2EInstallationExampleRestAssuredUsersExtensions extends NuRestAssureExtensions {

  implicit class UsersBasicAuth[T <: RequestSpecification](requestSpecification: T) {

    def basicAuthAdmin(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("admin", "admin")

  }

}
