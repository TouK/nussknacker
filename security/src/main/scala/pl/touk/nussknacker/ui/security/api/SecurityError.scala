package pl.touk.nussknacker.ui.security.api

sealed trait SecurityError
sealed trait AuthorizationError  extends SecurityError
sealed trait AuthenticationError extends SecurityError

object SecurityError {
  case object BaseAuthorizationError extends AuthorizationError

  case object ImpersonationMissingPermissionError extends AuthorizationError {
    val errorMessage = "The supplied authentication is not authorized to impersonate"
  }

  case object BaseAuthenticationError extends AuthenticationError

  case object ImpersonatedUserDataNotFoundError extends AuthenticationError {
    val errorMessage = "No impersonated user data found for provided identity"
  }

}
