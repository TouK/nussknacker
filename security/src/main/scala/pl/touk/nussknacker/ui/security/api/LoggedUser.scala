package pl.touk.nussknacker.ui.security.api

import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigRule
import GlobalPermission.GlobalPermission
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.security.api.CreationError.ImpersonationNotAllowed

sealed trait LoggedUser {
  val id: String
  val username: String
  val impersonatedBy: Option[LoggedUser]
  val isAdmin: Boolean
  def can(category: String, permission: Permission): Boolean
}

object LoggedUser {

  def create(
      authenticatedUser: AuthenticatedUser,
      rules: List[ConfigRule]
  ): Either[CreationError, LoggedUser] = {
    val loggedUser            = LoggedUser(authenticatedUser, rules)
    val impersonationChecking = new ImpersonationChecking(loggedUser)
    authenticatedUser.impersonatedAuthenticationUser match {
      case Some(impersonatedUser) =>
        if (impersonationChecking.canImpersonate) {
          Right(
            ImpersonatedUser(
              impersonatedUser = LoggedUser(impersonatedUser, rules),
              impersonatingUser = loggedUser
            )
          )
        } else Left(ImpersonationNotAllowed)
      case None => Right(loggedUser)
    }
  }

  def apply(
      authenticatedUser: BaseAuthenticationUserInfo,
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

}

final case class CommonUser(
    id: String,
    username: String,
    categoryPermissions: Map[String, Set[Permission]] = Map.empty,
    globalPermissions: List[GlobalPermission] = Nil
) extends LoggedUser {
  override val impersonatedBy: Option[LoggedUser] = None
  override val isAdmin: Boolean                   = false

  override def can(category: String, permission: Permission): Boolean = {
    categoryPermissions.get(category).exists(_ contains permission)
  }

  def categories(permission: Permission): Set[String] = categoryPermissions.collect {
    case (category, permissions) if permissions contains permission => category
  }.toSet

}

final case class AdminUser(id: String, username: String) extends LoggedUser {
  override val impersonatedBy: Option[LoggedUser]                     = None
  override val isAdmin: Boolean                                       = true
  override def can(category: String, permission: Permission): Boolean = true
}

final case class ImpersonatedUser(impersonatedUser: LoggedUser, impersonatingUser: LoggedUser) extends LoggedUser {
  override val id: String                                             = impersonatedUser.id
  override val username: String                                       = impersonatedUser.username
  override val impersonatedBy: Option[LoggedUser]                     = Some(impersonatingUser)
  override val isAdmin: Boolean                                       = impersonatedUser.isAdmin
  override def can(category: String, permission: Permission): Boolean = impersonatedUser.can(category, permission)
}

final class ImpersonationChecking(val user: LoggedUser) extends AnyVal {

  def canImpersonate: Boolean = user match {
    case _: AdminUser        => true
    case _: ImpersonatedUser => false
    case commonUser: CommonUser =>
      commonUser.globalPermissions
        .map(_.toLowerCase)
        .contains(Permission.Impersonate.toString.toLowerCase)
  }

}

sealed trait CreationError

object CreationError {
  case object ImpersonationNotAllowed extends CreationError
}
