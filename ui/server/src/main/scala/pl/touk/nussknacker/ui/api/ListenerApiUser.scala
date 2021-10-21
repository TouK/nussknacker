package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.listener.ListenerUser
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ListenerApiUser(val id: String,
                      val username: String,
                      val loggedUser: LoggedUser) extends ListenerUser {
}

object ListenerApiUser {
  def apply(loggedUser: LoggedUser): ListenerUser = {
    new ListenerApiUser(loggedUser.id, loggedUser.username, loggedUser)
  }
}
