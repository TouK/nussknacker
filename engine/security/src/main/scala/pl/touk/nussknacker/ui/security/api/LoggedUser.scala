package pl.touk.nussknacker.ui.security.api

import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission

sealed trait LoggedUser {
  val id: String
  val username: String
  val isAdmin: Boolean
  def can(category: String, permission: Permission): Boolean
}

object LoggedUser {
   def apply(id: String,
             username: String,
             categoryPermissions: Map[String, Set[Permission]] = Map.empty,
             globalPermissions: List[GlobalPermission] = Nil,
             isAdmin: Boolean = false): LoggedUser = {
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

case class CommonUser(id: String,
                      username: String,
                      categoryPermissions: Map[String, Set[Permission]] = Map.empty,
                      globalPermissions: List[GlobalPermission] = Nil) extends LoggedUser {
  def categories(permission: Permission): Set[String] = categoryPermissions.collect {
    case (category, permissions) if permissions contains permission => category
  }.toSet

  override def can(category: String, permission: Permission): Boolean = {
    categoryPermissions.get(category).exists(_ contains permission)
  }

  override val isAdmin: Boolean = false
}

case class AdminUser(id: String, username: String) extends LoggedUser {
  override def can(category: String, permission: Permission): Boolean = true
  override val isAdmin: Boolean = true
}
