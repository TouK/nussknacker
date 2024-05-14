package pl.touk.nussknacker.ui.security.basicauth

import pl.touk.nussknacker.ui.security.api.{ImpersonatedUserData, ImpersonationContext}

class BasicAuthImpersonationContext(config: BasicAuthenticationConfiguration) extends ImpersonationContext {

  override def getImpersonatedUserData(impersonatedUserIdentity: String): Option[ImpersonatedUserData] = {
    config.users
      .find { _.identity == impersonatedUserIdentity }
      .flatMap { configUser =>
        configUser.username match {
          case Some(username) => Some(ImpersonatedUserData(configUser.identity, username, configUser.roles))
          case None           => Some(ImpersonatedUserData(configUser.identity, configUser.identity, configUser.roles))
        }
      }
  }

}
