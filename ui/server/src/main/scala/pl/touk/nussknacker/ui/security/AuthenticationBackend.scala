package pl.touk.nussknacker.ui.security

object AuthenticationBackend extends Enumeration {
  type DefaultBackend = Value

  val BasicAuth = Value("BasicAuth")
  val Unknown = Value("Unknown")
  val OAuth2 = Value("OAuth2")
}
