package pl.touk.nussknacker.ui.security.api.ouath2

import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatest.{FunSuite, Matchers}
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.api.{GlobalPermission, Permission}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration.OAuth2ConfigRule
import pl.touk.nussknacker.ui.security.api.oauth2.RulesSet

class RulesSetSpec  extends FunSuite with Matchers with TableDrivenPropertyChecks {
  val emptyRule = OAuth2ConfigRule(role = "")

  test("getOnlyMatchingRules - normalize role name") {
    val readRule = emptyRule.copy(role = "ReAdeR", categories = List("Default", "FraudDetection"), permissions = List(Permission.Read))
    val rules = List(readRule)

    val table = Table[List[String], List[OAuth2ConfigRule], Map[String, Set[Permission]]](
      ("roles", "rules", "permissions"),
      (List("unknown role"), rules, Map.empty),
      (List(readRule.role), rules, Map("FraudDetection" -> Set(Permission.Read), "Default" -> Set(Permission.Read)))
    )

    forAll(table) { (roles: List[String], rules: List[OAuth2ConfigRule], permission: Map[String, Set[Permission]]) =>
      val rulesSet = RulesSet.getOnlyMatchingRules(roles, rules, List.empty)
      rulesSet.permissions shouldBe permission
    }
  }

  test("mapCategory - normalize category name") {
    val allCategories = List("Default", "FraudDetection")
    val readRule = emptyRule.copy(role = "ReAdeR", categories = List("DeFaulT", "frauddetection", "Recommendations"), permissions = List(Permission.Read))
    val rules = List(readRule)

    val table = Table[List[String], List[OAuth2ConfigRule], Map[String, Set[Permission]]](
      ("roles", "rules", "permissions"),
      (List("unknown role"), rules, Map.empty),
      (List(readRule.role), rules, Map("FraudDetection" -> Set(Permission.Read), "Default" -> Set(Permission.Read), "Recommendations" -> Set(Permission.Read)))
    )

    forAll(table) { (roles: List[String], rules: List[OAuth2ConfigRule], permission: Map[String, Set[Permission]]) =>
      val rulesSet = RulesSet.getOnlyMatchingRules(roles, rules, allCategories)
      rulesSet.permissions shouldBe permission
    }
  }

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
      val rulesSet = RulesSet.getOnlyMatchingRules(roles, rules, List.empty)
      rulesSet.permissions shouldBe permission
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
      val rulesSet = RulesSet.getOnlyMatchingRules(roles, rules, List.empty)
      rulesSet.globalPermissions shouldBe permissions
    }
  }
}
