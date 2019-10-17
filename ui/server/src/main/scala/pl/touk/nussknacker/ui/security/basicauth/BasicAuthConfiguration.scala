package pl.touk.nussknacker.ui.security.basicauth

import pl.touk.nussknacker.ui.security.{AuthenticationBackend, AuthenticationConfiguration}

case class BasicAuthConfiguration(backend: AuthenticationBackend.Value) extends AuthenticationConfiguration{
  override def getBackend(): AuthenticationBackend.Value = backend
}