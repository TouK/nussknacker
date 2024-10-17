package pl.touk.nussknacker.ui.security.api

sealed trait SecurityError
sealed trait AuthorizationError               extends SecurityError
sealed trait AuthenticationError              extends SecurityError
sealed trait ImpersonationAuthenticationError extends AuthenticationError

object SecurityError {

  case object InsufficientPermission extends AuthorizationError

  case object ImpersonationMissingPermissionError extends AuthorizationError

  case object ImpersonatedUserNotExistsError extends ImpersonationAuthenticationError

  case object CannotAuthenticateUser extends AuthenticationError

  case object ImpersonationNotSupportedError extends ImpersonationAuthenticationError

  implicit class SecurityErrorWithMessage(s: SecurityError) {

    def errorMessage: String = s match {
      case InsufficientPermission => "The supplied authentication is not authorized to access this resource"
      case ImpersonationMissingPermissionError => "The supplied authentication is not authorized to impersonate"
      case ImpersonatedUserNotExistsError      => "No impersonated user data found for provided identity"
      case CannotAuthenticateUser              => "The supplied authentication is invalid"
      case ImpersonationNotSupportedError      => "Provided authentication method does not support impersonation"
    }

  }

}
