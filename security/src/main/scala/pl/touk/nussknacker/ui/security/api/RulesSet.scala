package pl.touk.nussknacker.ui.security.api

import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigRule
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission

class RulesSet(rules: List[ConfigRule]) {
  import cats.instances.all._
  import cats.syntax.semigroup._

  def permissions: Map[String, Set[Permission]] = {
    rules
      .flatMap { rule =>
        rule.categories
          .map(_ -> (if (isAdmin) Permission.ALL_PERMISSIONS else rule.permissions.toSet))
      }
      .map(List(_).toMap)
      .foldLeft(Map.empty[String, Set[Permission]])(_ |+| _)
  }

  def globalPermissions: List[GlobalPermission] = {
    rules.flatMap(_.globalPermissions).distinct
  }

  def isAdmin: Boolean = rules.exists(rule => rule.isAdmin)

}

object RulesSet {

  def getOnlyMatchingRules(roles: List[String], rules: List[ConfigRule]): RulesSet = {
    val filtered = rules.filter(rule => roles.map(_.toLowerCase).contains(rule.role.toLowerCase))
    new RulesSet(filtered)
  }

}
