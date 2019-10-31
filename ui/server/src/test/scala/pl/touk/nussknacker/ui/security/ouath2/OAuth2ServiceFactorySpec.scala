package pl.touk.nussknacker.ui.security.ouath2

import org.scalatest.{FunSuite, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.ui.security.api.{GlobalPermission, Permission}
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration.OAuth2ConfigRule
import pl.touk.nussknacker.ui.security.oauth2.{OAuth2AuthenticatorFactory}

class OAuth2ServiceFactorySpec  extends FunSuite with Matchers with TableDrivenPropertyChecks {
  val emptyRule = OAuth2ConfigRule(roleName = "")

  test("isAdmin") {
    val userRule = emptyRule.copy(roleName = "userRole")
    val adminRule = emptyRule.copy(roleName = "adminRole", isAdmin = true)
    val rules = List(userRule, adminRule)
    val table = Table[List[String], List[OAuth2ConfigRule], Boolean](
      ("roles", "rules", "isAdmin"),
      (Nil, Nil, false),
      (List("unknown role"), rules, false),
      (List(userRule.roleName), rules, false),
      (List(adminRule.roleName), rules, true),
      (List(userRule.roleName, adminRule.roleName), rules, true)
    )

    forAll(table) { (roles: List[String], rules: List[OAuth2ConfigRule], isAdmin: Boolean) =>
      OAuth2AuthenticatorFactory.isAdmin(roles, rules) shouldBe isAdmin
    }
  }

  test("permissions") {
    val readRule = emptyRule.copy(roleName = "Reader", categories = List("Default", "FraudDetection"), permissions = List(Permission.Read))
    val writeRule = emptyRule.copy(roleName = "Writer", categories = List("Default", "FraudDetection", "Recommendations"), permissions = List(Permission.Write))
    val deployRule = emptyRule.copy(roleName = "Deploy", categories = List("Default", "FraudDetection", "Recommendations"), permissions = List(Permission.Deploy))
    val rules = List(readRule, writeRule, deployRule)

    val table = Table[List[String], List[OAuth2ConfigRule], Map[String, Set[Permission]]](
      ("roles", "rules", "permissions"),
      (List("unknown role"), rules, Map.empty),
      (List(readRule.roleName), rules, Map("FraudDetection" -> Set(Permission.Read), "Default" -> Set(Permission.Read))),
      (List(readRule.roleName, writeRule.roleName), rules, Map("FraudDetection" -> Set(Permission.Read, Permission.Write), "Default" -> Set(Permission.Read, Permission.Write), "Recommendations"-> Set(Permission.Write))),
      (List(deployRule.roleName, writeRule.roleName), rules, Map("FraudDetection" -> Set(Permission.Write, Permission.Deploy), "Default" -> Set(Permission.Write, Permission.Deploy), "Recommendations"-> Set(Permission.Write, Permission.Deploy))),
      (List(deployRule.roleName, readRule.roleName, writeRule.roleName), rules, Map("FraudDetection" -> Set(Permission.Write, Permission.Deploy, Permission.Read), "Default" -> Set(Permission.Write, Permission.Deploy, Permission.Read), "Recommendations"-> Set(Permission.Write, Permission.Deploy)))
    )

    forAll(table) { (roles: List[String], rules: List[OAuth2ConfigRule], permission: Map[String, Set[Permission]]) =>
      OAuth2AuthenticatorFactory.getPermissions(roles, rules) shouldBe permission
    }
  }

  test("global permissions") {
    val userRule = emptyRule.copy(roleName = "userRole")
    val adminRule = emptyRule.copy(roleName = "adminRole", isAdmin = true)
    val userRuleWithAdminTab = emptyRule.copy(roleName = "userRoleWithAdminTab", globalPermissions = GlobalPermission.AdminTab :: Nil)

    val rules = List(userRule, adminRule, userRuleWithAdminTab)
    val table = Table[List[String], List[OAuth2ConfigRule], List[GlobalPermission]](
      ("roles", "rules", "permissions"),
      (Nil, Nil, Nil),
      (List("unknown role"), rules, Nil),
      (List(userRule.roleName), rules, Nil),
      (List(userRuleWithAdminTab.roleName), rules, GlobalPermission.AdminTab :: Nil),
      (List(adminRule.roleName), rules, GlobalPermission.ALL_PERMISSIONS.toList),
      (List(userRule.roleName, adminRule.roleName), rules, GlobalPermission.ALL_PERMISSIONS.toList),
      (List(userRuleWithAdminTab.roleName, adminRule.roleName), rules, GlobalPermission.ALL_PERMISSIONS.toList),
      (List(userRule.roleName, userRuleWithAdminTab.roleName), rules, GlobalPermission.AdminTab :: Nil),
      (List(userRule.roleName, adminRule.roleName, userRuleWithAdminTab.roleName), rules, GlobalPermission.AdminTab :: Nil)
    )
    forAll(table) { (roles: List[String], rules: List[OAuth2ConfigRule], permissions: List[GlobalPermission]) =>
      OAuth2AuthenticatorFactory.getGlobalPermissions(roles, rules) shouldBe permissions
    }
  }
}
