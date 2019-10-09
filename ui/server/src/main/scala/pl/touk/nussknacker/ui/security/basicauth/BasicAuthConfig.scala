package pl.touk.nussknacker.ui.security.basicauth

import pl.touk.nussknacker.ui.security.{AuthenticationBackend, AuthenticationConfig}

case class BasicAuthConfig(backend: AuthenticationBackend.Value) extends AuthenticationConfig{
  override def getBackend(): AuthenticationBackend.Value = backend
}