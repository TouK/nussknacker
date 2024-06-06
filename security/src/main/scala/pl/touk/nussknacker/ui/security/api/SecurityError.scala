package pl.touk.nussknacker.ui.security.api

sealed trait SecurityError
sealed trait AuthorizationError               extends SecurityError
sealed trait AuthenticationError              extends SecurityError
sealed trait ImpersonationAuthenticationError extends AuthenticationError

object SecurityError {
  case object InsufficientPermission extends AuthorizationError

  case object ImpersonationMissingPermissionError extends AuthorizationError {
    val errorMessage = "The supplied authentication is not authorized to impersonate"
  }

  case object CannotAuthenticateUser extends AuthenticationError

  case object ImpersonatedUserDataNotFoundError extends ImpersonationAuthenticationError {
    val errorMessage = "No impersonated user data found for provided identity"
  }

  case object ImpersonationNotSupportedError extends ImpersonationAuthenticationError {
    val errorMessage = "Provided authentication method does not support impersonation"
  }

}
