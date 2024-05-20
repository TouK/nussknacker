package pl.touk.nussknacker.ui.security.api

import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigRule
import GlobalPermission.GlobalPermission
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.security.api.CreationError.ImpersonationNotAllowed

sealed trait LoggedUser {
  val id: String
  val username: String
  def can(category: String, permission: Permission): Boolean
}

sealed trait RealLoggedUser extends LoggedUser

object LoggedUser {

  def create(
      authenticatedUser: AuthenticatedUser,
      rules: List[ConfigRule]
  ): Either[CreationError, LoggedUser] = {
    val loggedUser = LoggedUser(authenticatedUser, rules)
    authenticatedUser.impersonatedAuthenticationUser match {
      case None =>
        Right(loggedUser)
      case Some(impersonatedUser) if loggedUser.canImpersonate =>
        Right(
          ImpersonatedUser(
            impersonatedUser = LoggedUser(impersonatedUser, rules),
            impersonatingUser = loggedUser
          )
        )
      case Some(_) =>
        Left(ImpersonationNotAllowed)
    }
  }

  def apply(
      authenticatedUser: AuthenticatedUser,
      rules: List[ConfigRule]
  ): RealLoggedUser = {
    val rulesSet = RulesSet.getOnlyMatchingRules(authenticatedUser.roles.toList, rules)
    apply(id = authenticatedUser.id, username = authenticatedUser.username, rulesSet = rulesSet)
  }

  def apply(
      id: String,
      username: String,
      categoryPermissions: Map[String, Set[Permission]] = Map.empty,
      globalPermissions: List[GlobalPermission] = Nil,
      isAdmin: Boolean = false
  ): RealLoggedUser = {
    if (isAdmin) {
      AdminUser(id, username)
    } else {
      CommonUser(id, username, categoryPermissions, globalPermissions)
    }
  }

  def apply(id: String, username: String, rulesSet: RulesSet): RealLoggedUser = {
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

  implicit class ImpersonationChecking(val user: LoggedUser) extends AnyVal {

    def canImpersonate: Boolean = user match {
      case _: AdminUser        => true
      case _: ImpersonatedUser => false
      case commonUser: CommonUser =>
        commonUser.globalPermissions
          .map(_.toLowerCase)
          .contains(Permission.Impersonate.toString.toLowerCase)
    }

  }

  implicit class isAdminChecking(val user: LoggedUser) extends AnyVal {

    def isAdmin: Boolean = user match {
      case _: AdminUser        => true
      case _: CommonUser       => false
      case u: ImpersonatedUser => u.impersonatedUser.isAdmin
    }

  }

}

final case class CommonUser(
    id: String,
    username: String,
    categoryPermissions: Map[String, Set[Permission]] = Map.empty,
    globalPermissions: List[GlobalPermission] = Nil
) extends RealLoggedUser {

  override def can(category: String, permission: Permission): Boolean = {
    categoryPermissions.get(category).exists(_ contains permission)
  }

  def categories(permission: Permission): Set[String] = categoryPermissions.collect {
    case (category, permissions) if permissions contains permission => category
  }.toSet

}

final case class AdminUser(id: String, username: String) extends RealLoggedUser {
  override def can(category: String, permission: Permission): Boolean = true
}

final case class ImpersonatedUser(impersonatedUser: RealLoggedUser, impersonatingUser: RealLoggedUser)
    extends LoggedUser {
  override val id: String                                             = impersonatedUser.id
  override val username: String                                       = impersonatedUser.username
  override def can(category: String, permission: Permission): Boolean = impersonatedUser.can(category, permission)
  val impersonatedBy: RealLoggedUser                                  = impersonatingUser
}

sealed trait CreationError

object CreationError {
  case object ImpersonationNotAllowed extends CreationError
}
