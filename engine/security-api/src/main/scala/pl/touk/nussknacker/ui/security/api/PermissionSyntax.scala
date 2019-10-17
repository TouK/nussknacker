package pl.touk.nussknacker.ui.security.api

object PermissionSyntax {
  implicit class UserPermissionsQuery(user: LoggedUser) {
    def can(category: String, permission: Permission.Permission): Boolean = {
      user.hasPermission(category, permission)
    }
  }
}
