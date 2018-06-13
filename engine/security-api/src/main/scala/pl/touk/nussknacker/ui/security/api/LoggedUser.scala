package pl.touk.nussknacker.ui.security.api

import Permission.Permission

case class LoggedUser(id: String,
                      categoryPermissions: Map[String, Set[Permission]]=Map.empty) {
  private val permissions = categoryPermissions.values.flatten.toSet
  def hasPermission(permission: Permission): Boolean = {
    permissions.contains(permission) || isAdmin
  }

  //TODO: remove
  def isAdmin: Boolean = permissions.contains(Permission.Admin)

}
