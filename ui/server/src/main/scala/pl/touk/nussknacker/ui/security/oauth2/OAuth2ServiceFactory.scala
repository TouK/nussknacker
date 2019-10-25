package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.util.ClassLoaderUtils


trait OAuth2Service {
  def clientApi: OAuth2ClientApi[_, _ <: AccessTokenResponseDefinition]
}

class DefaultOAuth2Service(val clientApi: OAuth2ClientApi[_, _ <: AccessTokenResponseDefinition], configuration: OAuth2Configuration) extends OAuth2Service with LazyLogging {


}

object OAuth2ServiceFactory {
  def apply(configuration: OAuth2Configuration, classLoader: ClassLoader): OAuth2Service = ClassLoaderUtils[OAuth2Service](classLoader).loadClass {
    val clientApi = OAuth2ClientApi[DefaultProfileResponse, DefaultAccessTokenResponse](configuration)
    new DefaultOAuth2Service(clientApi, configuration)
  }

  def apply(configuration: OAuth2Configuration, clientApi: OAuth2ClientApi[_, _ <: AccessTokenResponseDefinition]): OAuth2Service
    = new DefaultOAuth2Service(clientApi, configuration)
}