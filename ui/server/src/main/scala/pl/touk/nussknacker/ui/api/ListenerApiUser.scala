package pl.touk.nussknacker.ui.api

import pl.touk.nussknacker.ui.listener.services.ListenerUser
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ListenerApiUser(val id: String,
                      val username: String,
                      val loggedUser: LoggedUser) extends ListenerUser {
}

object ListenerApiUser {
  def apply(loggedUser: LoggedUser): Unit = {
    new ListenerApiUser(loggedUser.id, loggedUser.username, loggedUser)
  }
}
