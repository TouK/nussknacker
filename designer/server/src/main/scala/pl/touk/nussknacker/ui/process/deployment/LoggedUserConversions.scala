package pl.touk.nussknacker.ui.process.deployment

import pl.touk.nussknacker.engine.deployment.{User => ManagerUser}
import pl.touk.nussknacker.ui.security.api.LoggedUser

object LoggedUserConversions {

  implicit class LoggedUserOps(loggedUser: LoggedUser) {

    def toManagerUser: ManagerUser = ManagerUser(loggedUser.id, loggedUser.username)

  }

}
