package pl.touk.nussknacker.ui.security.ouath2

import org.scalatest.{FunSuite, Matchers}
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.ui.security.api.{GlobalPermission, Permission}
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration.OAuth2ConfigRule
import pl.touk.nussknacker.ui.security.oauth2.{OAuth2ServiceFactory}

class OAuth2ServiceFactorySpec  extends FunSuite with Matchers with TableDrivenPropertyChecks {
  val emptyRule = OAuth2ConfigRule(role = "")

  test("permissions") {
    val readRule = emptyRule.copy(role = "Reader", categories = List("Default", "FraudDetection"), permissions = List(Permission.Read))
    val writeRule = emptyRule.copy(role = "Writer", categories = List("Default", "FraudDetection", "Recommendations"), permissions = List(Permission.Write))
    val deployRule = emptyRule.copy(role = "Deploy", categories = List("Default", "FraudDetection", "Recommendations"), permissions = List(Permission.Deploy))
    val rules = List(readRule, writeRule, deployRule)

    val table = Table[List[String], List[OAuth2ConfigRule], Map[String, Set[Permission]]](
      ("roles", "rules", "permissions"),
      (List("unknown role"), rules, Map.empty),
      (List(readRule.role), rules, Map("FraudDetection" -> Set(Permission.Read), "Default" -> Set(Permission.Read))),
      (List(readRule.role, writeRule.role), rules, Map("FraudDetection" -> Set(Permission.Read, Permission.Write), "Default" -> Set(Permission.Read, Permission.Write), "Recommendations"-> Set(Permission.Write))),
      (List(deployRule.role, writeRule.role), rules, Map("FraudDetection" -> Set(Permission.Write, Permission.Deploy), "Default" -> Set(Permission.Write, Permission.Deploy), "Recommendations"-> Set(Permission.Write, Permission.Deploy))),
      (List(deployRule.role, readRule.role, writeRule.role), rules, Map("FraudDetection" -> Set(Permission.Write, Permission.Deploy, Permission.Read), "Default" -> Set(Permission.Write, Permission.Deploy, Permission.Read), "Recommendations"-> Set(Permission.Write, Permission.Deploy)))
    )

    forAll(table) { (roles: List[String], rules: List[OAuth2ConfigRule], permission: Map[String, Set[Permission]]) =>
      val matchedRoles = OAuth2ServiceFactory.getOnlyMatchingRoles(roles, rules)
      OAuth2ServiceFactory.getPermissions(matchedRoles) shouldBe permission
    }
  }

  test("global permissions") {
    val userRule = emptyRule.copy(role = "userRole")
    val adminRule = emptyRule.copy(role = "adminRole", isAdmin = true)
    val userRuleWithAdminTab = emptyRule.copy(role = "userRoleWithAdminTab", globalPermissions = GlobalPermission.AdminTab :: Nil)

    val rules = List(userRule, adminRule, userRuleWithAdminTab)
    val table = Table[List[String], List[OAuth2ConfigRule], List[GlobalPermission]](
      ("roles", "rules", "permissions"),
      (Nil, Nil, Nil),
      (List("unknown role"), rules, Nil),
      (List(userRule.role), rules, Nil),
      (List(userRuleWithAdminTab.role), rules, GlobalPermission.AdminTab :: Nil),
      (List(adminRule.role), rules, GlobalPermission.ALL_PERMISSIONS.toList),
      (List(userRule.role, adminRule.role), rules, GlobalPermission.ALL_PERMISSIONS.toList),
      (List(userRuleWithAdminTab.role, adminRule.role), rules, GlobalPermission.ALL_PERMISSIONS.toList),
      (List(userRule.role, userRuleWithAdminTab.role), rules, GlobalPermission.AdminTab :: Nil),
      (List(userRule.role, adminRule.role, userRuleWithAdminTab.role), rules, GlobalPermission.AdminTab :: Nil)
    )
    forAll(table) { (roles: List[String], rules: List[OAuth2ConfigRule], permissions: List[GlobalPermission]) =>
      val matchedRoles = OAuth2ServiceFactory.getOnlyMatchingRoles(roles, rules)
      OAuth2ServiceFactory.getGlobalPermissions(matchedRoles) shouldBe permissions
    }
  }
}
