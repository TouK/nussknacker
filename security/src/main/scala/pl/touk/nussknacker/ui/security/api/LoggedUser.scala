package pl.touk.nussknacker.ui.security.api

import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.security.Permission.Permission
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigRule
import pl.touk.nussknacker.ui.security.api.CreationError.ImpersonationNotAllowed

import GlobalPermission.GlobalPermission

sealed trait LoggedUser {
  val id: String
  val username: String
  def can(category: String, permission: Permission): Boolean
}

object LoggedUser {

  def create(
      authenticatedUser: AuthenticatedUser,
      rules: List[ConfigRule],
      isAdminImpersonationPossible: Boolean = false
  ): Either[CreationError, LoggedUser] = {
    val loggedUser = RealLoggedUser(authenticatedUser, rules)
    authenticatedUser.impersonatedAuthenticationUser match {
      case None =>
        Right(loggedUser)
      case Some(impersonatedUser) if loggedUser.canImpersonate =>
        createImpersonatedUser(
          loggedUser,
          RealLoggedUser(impersonatedUser, rules),
          isAdminImpersonationPossible
        )
      case Some(_) =>
        Left(ImpersonationNotAllowed)
    }
  }

  private def createImpersonatedUser(
      impersonatingUser: RealLoggedUser,
      impersonatedLoggedUser: RealLoggedUser,
      isAdminImpersonationPossible: Boolean
  ): Either[CreationError, LoggedUser] = impersonatedLoggedUser match {
    case _: CommonUser =>
      Right(ImpersonatedUser(impersonatedLoggedUser, impersonatingUser))
    case _: AdminUser if isAdminImpersonationPossible =>
      Right(ImpersonatedUser(impersonatedLoggedUser, impersonatingUser))
    case _: AdminUser =>
      Left(ImpersonationNotAllowed)
  }

  implicit class UserImpersonationSyntax(val user: LoggedUser) extends AnyVal {

    def canImpersonate: Boolean = user match {
      case _: AdminUser        => true
      case _: ImpersonatedUser => false
      case commonUser: CommonUser =>
        commonUser.globalPermissions
          .map(_.toLowerCase)
          .contains(Permission.Impersonate.toString.toLowerCase)
    }

    def isAdmin: Boolean = user match {
      case _: AdminUser        => true
      case _: CommonUser       => false
      case u: ImpersonatedUser => u.impersonatedUser.isAdmin
    }

    def impersonatingUserId: Option[String] = user match {
      case _: RealLoggedUser   => None
      case u: ImpersonatedUser => Some(u.impersonatingUser.id)
    }

    def impersonatingUserName: Option[String] = user match {
      case _: RealLoggedUser   => None
      case u: ImpersonatedUser => Some(u.impersonatingUser.username)
    }

  }

}

sealed trait RealLoggedUser extends LoggedUser

object RealLoggedUser {

  def apply(authenticatedUser: AuthenticatedUser, rules: List[ConfigRule]): RealLoggedUser = {
    val rulesSet = RulesSet.getOnlyMatchingRules(authenticatedUser.roles.toList, rules)
    RealLoggedUser(id = authenticatedUser.id, username = authenticatedUser.username, rulesSet = rulesSet)
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

  private def apply(id: String, username: String, rulesSet: RulesSet): RealLoggedUser = {
    if (rulesSet.isAdmin) {
      RealLoggedUser(id = id, username = username, isAdmin = true)
    } else {
      RealLoggedUser(
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
}

sealed trait CreationError

object CreationError {
  case object ImpersonationNotAllowed extends CreationError
}
