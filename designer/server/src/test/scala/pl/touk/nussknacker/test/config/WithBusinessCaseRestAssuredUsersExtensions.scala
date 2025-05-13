package pl.touk.nussknacker.test.config

import io.restassured.specification.RequestSpecification
import pl.touk.nussknacker.test.NuRestAssureExtensions

// It enriches rest assure directives with user specified in /config/business-cases/basicauth-users.conf
// which is used among designer configuration inside the same directory
trait WithBusinessCaseRestAssuredUsersExtensions extends NuRestAssureExtensions {

  implicit class UsersBasicAuth[T <: RequestSpecification](requestSpecification: T) {

    def basicAuthAdmin(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("admin", "admin")

    def basicAuthWriter(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("writer", "writer")

    def basicAuthAllPermUser(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("allpermuser", "allpermuser")

    def basicAuthNoPermUser(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("nopermuser", "nopermuser")

    def basicAuthUnknownUser(): RequestSpecification =
      requestSpecification.preemptiveBasicAuth("unknownuser", "wrongcredentials")
  }

  implicit class UsersImpersonation[T <: RequestSpecification](requestSpecification: T) {

    private val impersonationHeader = "Nu-Impersonate-User-Identity"

    def impersonateRemoteUser(): RequestSpecification =
      requestSpecification.header(impersonationHeader, "remoteUser")

  }

}
