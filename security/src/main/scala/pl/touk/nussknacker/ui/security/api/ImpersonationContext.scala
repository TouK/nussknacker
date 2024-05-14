package pl.touk.nussknacker.ui.security.api

import com.typesafe.config.Config
import sttp.client3.SttpBackend

import scala.concurrent.{ExecutionContext, Future}

trait ImpersonationContext {

  def getImpersonatedUserData(impersonatedUserIdentity: String): Option[ImpersonatedUserData]

}

object ImpersonationContext {

  def apply(config: Config, classLoader: ClassLoader, sttpBackend: SttpBackend[Future, Any])(
      implicit ec: ExecutionContext
  ): ImpersonationContext = {
    implicit val sttpBackendImplicit: SttpBackend[Future, Any] = sttpBackend
    AuthenticationProvider(config, classLoader).createImpersonationContext(config)
  }

}

final case class ImpersonatedUserData(id: String, username: String, roles: Set[String])
