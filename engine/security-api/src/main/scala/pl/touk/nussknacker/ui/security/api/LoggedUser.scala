package pl.touk.nussknacker.ui.security.api

import Permission.Permission

case class LoggedUser(id: String, password: String, permissions: List[Permission],
                      categories: List[String]) {
  def hasPermission(permission: Permission) = {
    permissions.contains(permission) || isAdmin
  }

  def isAdmin = permissions.contains(Permission.Admin)
}
