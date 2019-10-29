package pl.touk.nussknacker.ui.security

object AuthenticationBackend extends Enumeration {
  type DefaultBackend = Value

  val BasicAuth = Value("BasicAuth")
  val Other = Value("Other")
  val OAuth2 = Value("OAuth2")
}
