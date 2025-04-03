package pl.touk.nussknacker.ui.customhttpservice

import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.api.BaseHttpService.LogicResult
import pl.touk.nussknacker.ui.api.HttpServiceSupport
import pl.touk.nussknacker.ui.customhttpservice.services.TapirEndpointSupport
import pl.touk.nussknacker.ui.customhttpservice.services.TapirEndpointSupport.SecuredEndpoint
import pl.touk.nussknacker.ui.security.api.{AuthManager, LoggedUser}
import sttp.tapir.PublicEndpoint

import scala.concurrent.{ExecutionContext, Future}

class TapirEndpointSupportAdapter(authManager: AuthManager)(implicit executionContext: ExecutionContext)
    extends TapirEndpointSupport {

  private val httpServiceSupport = new HttpServiceSupport(authManager)

  override def authorizeAdminUser[BUSINESS_ERROR](
      credentials: AuthCredentials
  ): Future[LogicResult[BUSINESS_ERROR, LoggedUser]] =
    httpServiceSupport.authorizeAdminUser(credentials)

  override def authorizeKnownUser[BUSINESS_ERROR](
      credentials: AuthCredentials
  ): Future[LogicResult[BUSINESS_ERROR, LoggedUser]] =
    httpServiceSupport.authorizeKnownUser(credentials)

  override protected def withSecurity_[INPUT, BUSINESS_ERROR, OUTPUT, R](
      endpoint: PublicEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R]
  ): SecuredEndpoint[INPUT, BUSINESS_ERROR, OUTPUT, R] = {
    import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.toSecuredEndpoint
    toSecuredEndpoint(endpoint).withSecurity(authManager.authenticationEndpointInput())
  }

}
