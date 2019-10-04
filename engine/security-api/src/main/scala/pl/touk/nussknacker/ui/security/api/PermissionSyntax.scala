package pl.touk.nussknacker.ui.security.api

object PermissionSyntax {

  implicit class UserPermissionsQuery(user: LoggedUser) {
    def can(permission: Permission.Permission): Set[String] =
      user.categoryPermissions.collect{
        case (c, p) if p.exists(containsPermissionOrAdmin(permission)) =>
          c
      }.toSet

    def can(category:String,permission: Permission.Permission): Boolean =
      user.categoryPermissions
        .getOrElse(category, Set.empty)
        .exists(containsPermissionOrAdmin(permission))
  }
  private[api] def containsPermissionOrAdmin(expected: Permission.Permission)(userCategoryPermission:Permission.Permission):Boolean = {
    expected == userCategoryPermission || userCategoryPermission == Permission.Admin
  }

}
