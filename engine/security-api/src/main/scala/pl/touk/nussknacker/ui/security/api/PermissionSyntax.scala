package pl.touk.nussknacker.ui.security.api

object PermissionSyntax {

  implicit class UserPermissionsQuery(user: LoggedUser) {
    def can(permission: Permission.Permission): Set[String] =
      user.categoryPermissions.collect{
        case (c, p) if user.isAdmin || p.exists(containsPermission(permission)) => c
      }.toSet

    def can(category:String, permission: Permission.Permission): Boolean =
      user.isAdmin || user.categoryPermissions
        .getOrElse(category, Set.empty)
        .exists(containsPermission(permission))
  }

  private[api] def containsPermission(expected: Permission.Permission)(userCategoryPermission: Permission.Permission): Boolean =
    expected == userCategoryPermission
}
