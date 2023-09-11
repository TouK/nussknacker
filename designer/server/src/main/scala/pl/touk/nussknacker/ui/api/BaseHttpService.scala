package pl.touk.nussknacker.ui.api

import com.typesafe.config.Config
import pl.touk.nussknacker.ui.process.ProcessCategoryService
import pl.touk.nussknacker.ui.security.api.{AdminUser, AuthCredentials, AuthenticationConfiguration, AuthenticationResources, CommonUser, LoggedUser}

import scala.concurrent.{ExecutionContext, Future}

abstract class BaseHttpService(config: Config,
                               processCategoryService: ProcessCategoryService,
                               authenticator: AuthenticationResources)
                              (implicit executionContext: ExecutionContext) {

  protected def authorizeAdmin[ERROR](credentials: AuthCredentials): Future[Either[SecuredEndpointError[ERROR], LoggedUser]] = {
    authorize(credentials)
      .map {
        case right@Right(AdminUser(_, _)) => right
        case Right(_: CommonUser) => Left(SecuredEndpointError.AuthorizationError)
        case error@Left(_) => error
      }
  }

  protected def authorize[ERROR](credentials: AuthCredentials): Future[Either[SecuredEndpointError[ERROR], LoggedUser]] = {
    authenticator
      .authenticate(credentials)
      .map {
        case Some(user) if user.roles.nonEmpty =>
          Right(LoggedUser(
            authenticatedUser = user,
            rules = AuthenticationConfiguration.getRules(config),
            processCategories = processCategoryService.getAllCategories
          ))
        case Some(_) =>
          Left(SecuredEndpointError.AuthorizationError)
        case None =>
          Left(SecuredEndpointError.AuthenticationError)
      }
  }
}
