package pl.touk.nussknacker.ui.security.api

import Permission.Permission

case class LoggedUser(id: String,
                      permissions: List[Permission],
                      categories: List[String]) {
  def hasPermission(permission: Permission): Boolean = {
    permissions.contains(permission) || isAdmin
  }

  def isAdmin: Boolean = permissions.contains(Permission.Admin)
}
