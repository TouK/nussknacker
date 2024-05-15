package pl.touk.nussknacker.ui.security.api

import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigRule

final case class ImpersonationError(message: String)

object AuthenticatedToLoggedUserConverter {

  def convertToLoggedUser(
      authenticatedUser: AuthenticatedUser,
      rules: List[ConfigRule]
  ): Either[ImpersonationError, LoggedUser] = {
    val baseLoggedUser = LoggedUser(authenticatedUser, rules)
    authenticatedUser.impersonatedUser match {
      case Some(impersonatedUser) =>
        if (baseLoggedUser.canImpersonate)
          Right(LoggedUser.createImpersonatedLoggedUser(baseLoggedUser, impersonatedUser, rules))
        else
          Left(
            ImpersonationError(
              s"user [${baseLoggedUser.username}] tried to impersonate user [${impersonatedUser.username}] but does not have appropriate permission."
            )
          )
      case None => Right(baseLoggedUser)
    }
  }

}
