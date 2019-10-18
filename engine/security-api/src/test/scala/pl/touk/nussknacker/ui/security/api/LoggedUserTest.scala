package pl.touk.nussknacker.ui.security.api

import org.scalatest.{FunSuite, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks._
import Permission._
import org.scalatest.prop.{TableFor3, TableFor4}

class LoggedUserTest extends FunSuite with Matchers {

  test("Admin permission grants other permissions") {
    def admin(cp: Map[String, Set[Permission]]) = LoggedUser("admin", cp, true)

    val perms: TableFor3[LoggedUser, Permission, String] = Table(
      ("user", "permission", "category"),
      (admin(Map("c1"->Set(Read))), Read, "c1"),
      (admin(Map.empty), Read, "c1"),
      (admin(Map("c2"->Set(Write))), Write, "c2"),
      (admin(Map.empty), Write, "c2")
    )

    forAll(perms) { (user: LoggedUser, p: Permission, c: String) =>
      user.can(c, p) shouldEqual true
    }
  }

  test("check user permission in category") {
    def u(m: Map[String, Set[Permission]]) = LoggedUser("", categoryPermissions = m)

    val perms: TableFor4[LoggedUser, Permission, String, Boolean] = Table(
      ("categoryPermissions", "permission", "category", "result"),
      (u(Map("c1"->Set(Read))), Read, "c1", true),
      (u(Map("c2"->Set(Read))), Read, "c1", false),
      (u(Map("c1"->Set(Write))), Read, "c1", false)
    )
    forAll(perms) { (u: LoggedUser, p: Permission, c: String, r:Boolean) =>
      u.can(c,p) shouldEqual r
    }
  }
}
