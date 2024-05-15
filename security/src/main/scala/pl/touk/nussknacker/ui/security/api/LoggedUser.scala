package pl.touk.nussknacker.ui.security.api

import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigRule
import GlobalPermission.GlobalPermission
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission

sealed trait LoggedUser {
  val id: String
  val username: String
  val isAdmin: Boolean
  val impersonatedBy: Option[LoggedUser]
  def can(category: String, permission: Permission): Boolean
  def canImpersonate: Boolean
}

object LoggedUser {

  def apply(
      authenticatedUser: AuthenticatedUser,
      rules: List[ConfigRule]
  ): LoggedUser = {
    val rulesSet = RulesSet.getOnlyMatchingRules(authenticatedUser.roles.toList, rules)
    apply(id = authenticatedUser.id, username = authenticatedUser.username, rulesSet = rulesSet)
  }

  def apply(
      id: String,
      username: String,
      categoryPermissions: Map[String, Set[Permission]] = Map.empty,
      globalPermissions: List[GlobalPermission] = Nil,
      isAdmin: Boolean = false
  ): LoggedUser = {
    if (isAdmin) {
      AdminUser(id, username)
    } else {
      CommonUser(id, username, categoryPermissions, globalPermissions)
    }
  }

  def apply(id: String, username: String, rulesSet: RulesSet): LoggedUser = {
    if (rulesSet.isAdmin) {
      LoggedUser(id = id, username = username, isAdmin = true)
    } else {
      LoggedUser(
        id = id,
        username = username,
        categoryPermissions = rulesSet.permissions,
        globalPermissions = rulesSet.globalPermissions
      )
    }
  }

  def createImpersonatedLoggedUser(
      loggedImpersonatingUser: LoggedUser,
      impersonatedUser: ImpersonatedUser,
      rules: List[ConfigRule]
  ): LoggedUser = {
    val impersonatedUserRules = RulesSet.getOnlyMatchingRules(impersonatedUser.roles.toList, rules)
    CommonUser(
      id = impersonatedUser.id,
      username = impersonatedUser.username,
      categoryPermissions = impersonatedUserRules.permissions,
      globalPermissions = impersonatedUserRules.globalPermissions,
      impersonatedBy = Some(loggedImpersonatingUser)
    )
  }

}

final case class CommonUser(
    id: String,
    username: String,
    categoryPermissions: Map[String, Set[Permission]] = Map.empty,
    globalPermissions: List[GlobalPermission] = Nil,
    impersonatedBy: Option[LoggedUser] = None
) extends LoggedUser {

  def categories(permission: Permission): Set[String] = categoryPermissions.collect {
    case (category, permissions) if permissions contains permission => category
  }.toSet

  override def can(category: String, permission: Permission): Boolean = {
    categoryPermissions.get(category).exists(_ contains permission)
  }

  override val isAdmin: Boolean = false

  override def canImpersonate: Boolean = globalPermissions
    .map(_.toLowerCase)
    .contains(Permission.OverrideUsername.toString.toLowerCase)

}

final case class AdminUser(id: String, username: String) extends LoggedUser {
  override def can(category: String, permission: Permission): Boolean = true
  override val isAdmin: Boolean                                       = true
  override val impersonatedBy: Option[LoggedUser]                     = None
  override def canImpersonate: Boolean                                = true
}
