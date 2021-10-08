package pl.touk.nussknacker.ui.security.api

import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigRule
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission

class RulesSet(rules: List[ConfigRule], allCategories: List[String]) {
  import cats.instances.all._
  import cats.syntax.semigroup._

  def permissions: Map[String, Set[Permission]] = {
    rules.flatMap { rule =>
      rule.categories
        .flatMap(matchCategory)
        .map(_ -> (if (isAdmin) Permission.ALL_PERMISSIONS else rule.permissions.toSet))
    }.map(List(_).toMap)
      .foldLeft(Map.empty[String, Set[Permission]])(_ |+| _)
  }

  def globalPermissions: List[GlobalPermission] = {
    rules.flatMap(_.globalPermissions).distinct
  }

  def isAdmin: Boolean = rules.exists(rule => rule.isAdmin)

  private def matchCategory(category: String): Option[String] =
    allCategories
      .find(c => c.toLowerCase.equals(category.toLowerCase))
      .orElse(Option(category)) // If category not exists at systemCategories - allCategories then return base category
}

object RulesSet {
  def getOnlyMatchingRules(roles: List[String], rules: List[ConfigRule], allCategories: List[String]): RulesSet = {
    val filtered = rules.filter(rule => roles.map(_.toLowerCase).contains(rule.role.toLowerCase))
    new RulesSet(filtered, allCategories)
  }
}