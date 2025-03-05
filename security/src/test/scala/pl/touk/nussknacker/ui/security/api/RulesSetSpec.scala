package pl.touk.nussknacker.ui.security.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigRule

import GlobalPermission.GlobalPermission

class RulesSetSpec extends AnyFunSuite with Matchers with TableDrivenPropertyChecks {
  val emptyRule = ConfigRule(role = "")

  test("getOnlyMatchingRules - normalize role name") {
    val readRule = emptyRule.copy(
      role = "ReAdeR",
      categories = List("Default", "FraudDetection"),
      permissions = List(Permission.Read)
    )
    val rules = List(readRule)

    val table = Table[List[String], List[ConfigRule], Map[String, Set[Permission]]](
      ("roles", "rules", "permissions"),
      (List("unknown role"), rules, Map.empty),
      (List(readRule.role), rules, Map("FraudDetection" -> Set(Permission.Read), "Default" -> Set(Permission.Read)))
    )

    forAll(table) { (roles: List[String], rules: List[ConfigRule], permission: Map[String, Set[Permission]]) =>
      val rulesSet = RulesSet.getOnlyMatchingRules(roles, rules)
      rulesSet.permissions shouldBe permission
    }
  }

  test("permissions") {
    val readRule = emptyRule.copy(
      role = "Reader",
      categories = List("Default", "FraudDetection"),
      permissions = List(Permission.Read)
    )
    val writeRule = emptyRule.copy(
      role = "Writer",
      categories = List("Default", "FraudDetection", "Recommendations"),
      permissions = List(Permission.Write)
    )
    val deployRule = emptyRule.copy(
      role = "Deploy",
      categories = List("Default", "FraudDetection", "Recommendations"),
      permissions = List(Permission.Deploy)
    )
    val rules = List(readRule, writeRule, deployRule)

    val table = Table[List[String], List[ConfigRule], Map[String, Set[Permission]]](
      ("roles", "rules", "permissions"),
      (List("unknown role"), rules, Map.empty),
      (List(readRule.role), rules, Map("FraudDetection" -> Set(Permission.Read), "Default" -> Set(Permission.Read))),
      (
        List(readRule.role, writeRule.role),
        rules,
        Map(
          "FraudDetection"  -> Set(Permission.Read, Permission.Write),
          "Default"         -> Set(Permission.Read, Permission.Write),
          "Recommendations" -> Set(Permission.Write)
        )
      ),
      (
        List(deployRule.role, writeRule.role),
        rules,
        Map(
          "FraudDetection"  -> Set(Permission.Write, Permission.Deploy),
          "Default"         -> Set(Permission.Write, Permission.Deploy),
          "Recommendations" -> Set(Permission.Write, Permission.Deploy)
        )
      ),
      (
        List(deployRule.role, readRule.role, writeRule.role),
        rules,
        Map(
          "FraudDetection"  -> Set(Permission.Write, Permission.Deploy, Permission.Read),
          "Default"         -> Set(Permission.Write, Permission.Deploy, Permission.Read),
          "Recommendations" -> Set(Permission.Write, Permission.Deploy)
        )
      )
    )

    forAll(table) { (roles: List[String], rules: List[ConfigRule], permission: Map[String, Set[Permission]]) =>
      val rulesSet = RulesSet.getOnlyMatchingRules(roles, rules)
      rulesSet.permissions shouldBe permission
    }
  }

  test("global permissions") {
    val adminTab = "AdminTab"

    val userRule             = emptyRule.copy(role = "userRole")
    val adminRule            = emptyRule.copy(role = "adminRole", isAdmin = true)
    val userRuleWithAdminTab = emptyRule.copy(role = "userRoleWithAdminTab", globalPermissions = adminTab :: Nil)

    val rules = List(userRule, adminRule, userRuleWithAdminTab)
    val table = Table[List[String], List[ConfigRule], List[GlobalPermission]](
      ("roles", "rules", "permissions"),
      (Nil, Nil, Nil),
      (List("unknown role"), rules, Nil),
      (List(userRule.role), rules, Nil),
      (List(userRuleWithAdminTab.role), rules, adminTab :: Nil),
      (List(userRule.role, userRuleWithAdminTab.role), rules, adminTab :: Nil),
      (List(userRule.role, adminRule.role, userRuleWithAdminTab.role), rules, adminTab :: Nil)
    )
    forAll(table) { (roles: List[String], rules: List[ConfigRule], permissions: List[GlobalPermission]) =>
      val rulesSet = RulesSet.getOnlyMatchingRules(roles, rules)
      rulesSet.globalPermissions shouldBe permissions
    }
  }

}
