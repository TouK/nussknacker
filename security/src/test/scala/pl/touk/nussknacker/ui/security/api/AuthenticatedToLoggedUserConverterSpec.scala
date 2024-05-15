package pl.touk.nussknacker.ui.security.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.security.Permission.{Deploy, Read, Write}
import pl.touk.nussknacker.ui.security.api.AuthenticatedToLoggedUserConverter.convertToLoggedUser
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigRule

class AuthenticatedToLoggedUserConverterSpec extends AnyFunSuite with Matchers {

  test("should convert authenticated user to logged user without impersonation") {
    val rules             = List(ConfigRule("Editor", categories = List("Category"), permissions = List(Read, Write)))
    val authenticatedUser = AuthenticatedUser("userId", "userName", Set("Editor"))

    val userConversion = convertToLoggedUser(authenticatedUser, rules)

    userConversion shouldBe Right(LoggedUser(authenticatedUser, rules))
  }

  test("should convert authenticated user to logged user with impersonation and appropriate permission") {
    val rules = List(
      ConfigRule("Editor", categories = List("Category"), permissions = List(Read, Write)),
      ConfigRule(
        role = "Technical",
        categories = List("Category"),
        permissions = List(Deploy),
        globalPermissions = List("OverrideUsername")
      )
    )
    val impersonatedUser = ImpersonatedUser("impersonatedUserId", "impersonatedUserName", Set("Editor"))
    val authenticatedUser = AuthenticatedUser(
      id = "technicalUserId",
      username = "technicalUserName",
      roles = Set("Technical"),
      impersonatedUser = Some(impersonatedUser)
    )

    val userConversion = convertToLoggedUser(authenticatedUser, rules)

    userConversion match {
      case Right(user) => {
        user.id shouldEqual "impersonatedUserId"
        user.username shouldEqual "impersonatedUserName"
        user.impersonatedBy.nonEmpty shouldEqual true
      }
      case Left(_) => fail("Expected a Right but got a Left")
    }
  }

  test("should not convert authenticated user to logged user with impersonation and without permission") {
    val rules = List(
      ConfigRule("Editor", categories = List("Category"), permissions = List(Read, Write)),
      ConfigRule("Writer", categories = List("Category"), permissions = List(Write))
    )
    val impersonatedUser = ImpersonatedUser("userId", "userName", Set("Editor"))
    val authenticatedUser = AuthenticatedUser(
      id = "writerUserId",
      username = "writerUserName",
      roles = Set("Writer"),
      impersonatedUser = Some(impersonatedUser)
    )

    val userConversion = convertToLoggedUser(authenticatedUser, rules)

    userConversion match {
      case Right(_) => fail("Expected a Right but got a Left")
      case Left(error) =>
        error.message shouldEqual "user [writerUserName] tried to impersonate user [userName] but does not have appropriate permission."
    }
  }

}
