package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.listener.User
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ListenerApiUser(val id: String,
                      val username: String,
                      val loggedUser: LoggedUser) extends User {
}

object ListenerApiUser {
  def apply(loggedUser: LoggedUser): User = {
    new ListenerApiUser(loggedUser.id, loggedUser.username, loggedUser)
  }
}
