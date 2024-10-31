package pl.touk.nussknacker.ui.util

import pl.touk.nussknacker.engine.api.deployment.{ScenarioUser, UserId, UserName}
import pl.touk.nussknacker.ui.security.api.LoggedUser

object LoggedUserUtils {

  implicit class Ops(loggedUser: LoggedUser) {

    def scenarioUser: ScenarioUser = ScenarioUser(
      id = Some(UserId(loggedUser.id)),
      name = UserName(loggedUser.username),
      impersonatedByUserId = loggedUser.impersonatingUserId.map(UserId.apply),
      impersonatedByUserName = loggedUser.impersonatingUserName.map(UserName.apply)
    )

  }

}
