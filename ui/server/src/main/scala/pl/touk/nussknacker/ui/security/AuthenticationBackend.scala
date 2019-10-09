package pl.touk.nussknacker.ui.security

object AuthenticationBackend extends Enumeration {
  type DefaultBackend = Value

  val BasicAuth = Value("BasicAuth")
  val OAuth2 = Value("OAuth2")
}
