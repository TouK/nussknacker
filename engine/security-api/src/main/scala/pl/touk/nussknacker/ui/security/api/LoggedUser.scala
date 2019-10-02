package pl.touk.nussknacker.ui.security.api

import pl.touk.nussknacker.ui.security.api.Permission.Permission

case class LoggedUser(
  id: String,
  categoryPermissions: Map[String, Set[Permission]] = Map.empty,
  isAdmin: Boolean = false
) {
  private val permissions = categoryPermissions.values.flatten.toSet

  def hasPermission(permission: Permission): Boolean = {
    isAdmin || permissions.contains(permission)
  }
}
