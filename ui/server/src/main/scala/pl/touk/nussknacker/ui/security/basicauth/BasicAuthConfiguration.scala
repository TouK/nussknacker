package pl.touk.nussknacker.ui.security.basicauth

import pl.touk.nussknacker.ui.security.{AuthenticationBackend, DefaultAuthenticationConfiguration}

class BasicAuthConfiguration(backend: AuthenticationBackend.Value, usersFile: String)
  extends DefaultAuthenticationConfiguration(backend: AuthenticationBackend.Value, usersFile: String)
