package pl.touk.nussknacker.ui.security.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableFor2, TableFor3, TableFor4}
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigRule
import pl.touk.nussknacker.ui.security.api.CreationError.ImpersonationNotAllowed

class LoggedUserTest extends AnyFunSuite with Matchers {
  import pl.touk.nussknacker.security.Permission._

  test("Admin permission grants other permissions") {
    def admin(cp: Map[String, Set[Permission]]) = RealLoggedUser("1", "admin", cp, isAdmin = true)

    val perms: TableFor3[LoggedUser, Permission, String] = Table(
      ("user", "permission", "category"),
      (admin(Map("c1" -> Set(Read))), Read, "c1"),
      (admin(Map.empty), Read, "c1"),
      (admin(Map("c2" -> Set(Write))), Write, "c2"),
      (admin(Map.empty), Write, "c2")
    )

    forAll(perms) { (user: LoggedUser, p: Permission, c: String) =>
      user.can(c, p) shouldEqual true
      user.username shouldBe "admin"
    }
  }

  test("check user permission in category") {
    def u(m: Map[String, Set[Permission]]) = RealLoggedUser("user", "user", categoryPermissions = m)

    val perms: TableFor4[LoggedUser, Permission, String, Boolean] = Table(
      ("categoryPermissions", "permission", "category", "result"),
      (u(Map("c1" -> Set(Read))), Read, "c1", true),
      (u(Map("c2" -> Set(Read))), Read, "c1", false),
      (u(Map("c1" -> Set(Write))), Read, "c1", false)
    )
    forAll(perms) { (u: LoggedUser, p: Permission, c: String, r: Boolean) =>
      u.can(c, p) shouldEqual r
      u.username shouldBe "user"
    }
  }

  test("should map an AuthenticatedUser to a LoggedUser with permissions matching roles") {
    val rules = List(
      ConfigRule("FirstDeployer", categories = List("First"), permissions = List(Read, Deploy)),
      ConfigRule("FirstEditor", categories = List("First"), permissions = List(Read, Write)),
      ConfigRule("SecondDeployer", categories = List("Second"), permissions = List(Read, Deploy)),
      ConfigRule("SecondEditor", categories = List("Second"), permissions = List(Read, Write))
    )

    val authorizedUser = RealLoggedUser(
      AuthenticatedUser("userId", "userName", Set("FirstDeployer", "SecondEditor")),
      rules
    )

    authorizedUser.can("First", Read) shouldBe true
    authorizedUser.can("First", Deploy) shouldBe true
    authorizedUser.can("First", Write) shouldBe false
    authorizedUser.can("Second", Read) shouldBe true
    authorizedUser.can("Second", Deploy) shouldBe false
    authorizedUser.can("Second", Write) shouldBe true
  }

  test("should create logged user without impersonation") {
    val rules             = List(ConfigRule("Editor", categories = List("Category"), permissions = List(Read, Write)))
    val authenticatedUser = AuthenticatedUser("userId", "userName", Set("Editor"))

    val loggedUser = LoggedUser.create(authenticatedUser, rules)

    loggedUser shouldBe Right(RealLoggedUser(authenticatedUser, rules))
  }

  test("should create impersonated user when impersonating with appropriate permission") {
    val rules = List(
      ConfigRule("Editor", categories = List("Category"), permissions = List(Read, Write)),
      ConfigRule(
        role = "Technical",
        categories = List("Category"),
        permissions = List(Deploy),
        globalPermissions = List("Impersonate")
      )
    )
    val impersonatedUser = AuthenticatedUser("impersonatedUserId", "impersonatedUserName", Set("Editor"))
    val authenticatedUser = AuthenticatedUser(
      id = "technicalUserId",
      username = "technicalUserName",
      roles = Set("Technical"),
      impersonatedAuthenticationUser = Some(impersonatedUser)
    )

    val maybeLoggedUser = LoggedUser.create(authenticatedUser, rules)

    maybeLoggedUser match {
      case Right(user: ImpersonatedUser) =>
        user.id shouldEqual "impersonatedUserId"
        user.username shouldEqual "impersonatedUserName"
        user.impersonatingUser.id shouldEqual authenticatedUser.id
      case Right(_) => fail("Expected a ImpersonatedUser")
      case Left(_)  => fail("Expected a Right but got a Left")
    }
  }

  test("should create impersonated admin user when admin impersonation is possible") {
    val rules = List(
      ConfigRule("Admin", isAdmin = true, categories = List("Category"), permissions = List(Read, Write)),
      ConfigRule(
        role = "Technical",
        categories = List("Category"),
        permissions = List(Deploy),
        globalPermissions = List("Impersonate")
      )
    )
    val impersonatedUser = AuthenticatedUser("adminId", "admin", Set("Admin"))
    val authenticatedUser = AuthenticatedUser(
      id = "technicalUserId",
      username = "technicalUserName",
      roles = Set("Technical"),
      impersonatedAuthenticationUser = Some(impersonatedUser)
    )

    val maybeLoggedUser = LoggedUser.create(authenticatedUser, rules, isAdminImpersonationPossible = true)

    maybeLoggedUser match {
      case Right(user: ImpersonatedUser) =>
        user.id shouldEqual "adminId"
        user.username shouldEqual "admin"
        user.impersonatingUser.id shouldEqual authenticatedUser.id
      case Right(_) => fail("Expected a ImpersonatedUser")
      case Left(_)  => fail("Expected a Right but got a Left")
    }
  }

  test("should not create impersonated user when impersonating without appropriate permission") {
    val rules = List(
      ConfigRule("Editor", categories = List("Category"), permissions = List(Read, Write)),
      ConfigRule("Writer", categories = List("Category"), permissions = List(Write))
    )
    val impersonatedUser = AuthenticatedUser("userId", "userName", Set("Editor"))
    val authenticatedUser = AuthenticatedUser(
      id = "writerUserId",
      username = "writerUserName",
      roles = Set("Writer"),
      impersonatedAuthenticationUser = Some(impersonatedUser)
    )

    val userConversion = LoggedUser.create(authenticatedUser, rules)

    userConversion match {
      case Right(_)                      => fail("Expected a Right but got a Left")
      case Left(ImpersonationNotAllowed) => ()
    }
  }

  test("should not create impersonated admin user when admin impersonation is not possible") {
    val rules = List(
      ConfigRule("Admin", isAdmin = true, categories = List("Category"), permissions = List(Read, Write)),
      ConfigRule(
        role = "Technical",
        categories = List("Category"),
        permissions = List(Deploy),
        globalPermissions = List("Impersonate")
      )
    )
    val impersonatedUser = AuthenticatedUser("adminId", "admin", Set("Admin"))
    val authenticatedUser = AuthenticatedUser(
      id = "technicalUserId",
      username = "technicalUserName",
      roles = Set("Technical"),
      impersonatedAuthenticationUser = Some(impersonatedUser)
    )

    val maybeLoggedUser = LoggedUser.create(authenticatedUser, rules)

    maybeLoggedUser match {
      case Right(_)                      => fail("Expected a Right but got a Left")
      case Left(ImpersonationNotAllowed) => ()
    }
  }

  test("should check whether logged user can impersonate") {
    val adminUser = AdminUser("adminId", "adminUsername")
    val commonUserWithImpersonatePermission = CommonUser(
      id = "userId",
      username = "username",
      categoryPermissions = Map("Category" -> Set(Read, Write)),
      globalPermissions = List("Impersonate")
    )
    val commonUserWithoutImpersonatePermission = CommonUser(
      id = "userId",
      username = "username",
      categoryPermissions = Map("Category" -> Set(Read, Write))
    )
    val impersonatedUser = ImpersonatedUser(adminUser, commonUserWithoutImpersonatePermission)

    val users: TableFor2[LoggedUser, Boolean] = Table(
      ("loggedUser", "canImpersonate"),
      (adminUser, true),
      (commonUserWithImpersonatePermission, true),
      (commonUserWithoutImpersonatePermission, false),
      (impersonatedUser, false),
    )

    forAll(users) { (loggedUser: LoggedUser, canImpersonate: Boolean) =>
      loggedUser.canImpersonate shouldEqual canImpersonate
    }
  }

}
