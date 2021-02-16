package pl.touk.nussknacker.ui.security.oauth2

import cats.data.Validated.{Invalid, Valid}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Decoder
import pl.touk.nussknacker.engine.util.cache.{CacheConfig, DefaultAsyncCache, ExpiryConfig}
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ClientApi.DefaultAccessTokenResponse
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ErrorHandler.OAuth2CompoundException
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Deadline, FiniteDuration, MILLISECONDS}
import scala.concurrent.{ExecutionContext, Future}


class DefaultOAuth2Service[ProfileResponse: Decoder](clientApi: OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse],
                                                     oAuth2Profile: OAuth2Profile[ProfileResponse],
                                                     configuration: OAuth2Configuration,
                                                     allCategories: List[String]) extends OAuth2Service with LazyLogging {

  private val authorizationsCache = new DefaultAsyncCache[String, (LoggedUser, Deadline)](CacheConfig(new ExpiryConfig[String, (LoggedUser, Deadline)]() {
    override def expireAfterWriteFn(key: String, value: (LoggedUser, Deadline), now: Deadline): Option[Deadline] = Some(value._2)
  }))

  private val jwtValidator = configuration.jwt.filter(_.enabled).map(new JwtValidator[ProfileResponse](_))

  def authenticate(code: String): Future[OAuth2AuthenticateData] = {
    clientApi.accessTokenRequest(code).map { resp: DefaultAccessTokenResponse =>
      authorizationsCache.getOrCreate(resp.access_token) {
        (createExpiringLoggedUser _).tupled(successfulTokenIntrospection(resp.token_type, resp.expires_in))
      }
      OAuth2AuthenticateData(
        access_token = resp.access_token,
        token_type = resp.token_type,
        refresh_token = resp.refresh_token
      )
    }
  }

  def authorize(token: String): Future[LoggedUser] =
    authorizationsCache.getOrCreate(token) {
      introspectToken(token).flatMap((createExpiringLoggedUser _).tupled)
    }.map { case (user, _) => user }

  def createExpiringLoggedUser(token: String, expiration: Deadline): Future[(LoggedUser, Deadline)] = {
    getProfile(token).map(oAuth2Profile.getLoggedUser(_, configuration, allCategories)).map((_, expiration))
  }

  def introspectToken(token: String): Future[(String, Deadline)] = {
    //TODO: validate the token if it is an JWT or call an introspection endpoint otherwise
    Future(successfulTokenIntrospection(token, None))
  }

  private def successfulTokenIntrospection(token: String, expiration: Option[Long]): (String, Deadline) =
    (token, expiration.map(FiniteDuration(_, MILLISECONDS)).map(Deadline(_)).getOrElse(Deadline.now + configuration.defaultTokenExpirationTime))

  /**
   * First checks whether a profile can be obtained from the token (provided authentication.jwt configured),
   * then tries to obtain the profile from a sent request.
   */
  private def getProfile(token: String): Future[ProfileResponse] = {
    jwtValidator.map(_.getProfileFromJwt(token)) match {
      case Some(Valid(profile)) => Future(profile)
      case Some(Invalid(jwtErrors)) => clientApi.profileRequest(token) recover { case OAuth2CompoundException(requestErrors) =>
        throw OAuth2CompoundException(jwtErrors concatNel requestErrors)
      }
      case None => clientApi.profileRequest(token)
    }
  }
}

class DefaultOAuth2ServiceFactoryWithProfileFormat[ProfileResponse: Decoder](oAuth2Profile: OAuth2Profile[ProfileResponse]) {
  def defaultService(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    new DefaultOAuth2Service[ProfileResponse](OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse](configuration), oAuth2Profile, configuration, allCategories)

  def service(configuration: OAuth2Configuration, allCategories: List[String])(implicit backend: SttpBackend[Future, Nothing, NothingT], ec: ExecutionContext): OAuth2Service =
    new DefaultOAuth2Service[ProfileResponse](new OAuth2ClientApi[ProfileResponse, DefaultAccessTokenResponse](configuration), oAuth2Profile, configuration, allCategories)
}

object DefaultOAuth2ServiceFactoryWithProfileFormat {
  def apply(configuration: OAuth2Configuration, allCategories: List[String]) = {
    configuration.profileFormat.getOrElse {
      throw new Exception("profileFormat is missing in the authentication configuration")
    } match {
      case ProfileFormat.GITHUB => new DefaultOAuth2ServiceFactoryWithProfileFormat[GitHubProfileResponse](GitHubProfile)
      case ProfileFormat.AUTH0 => new DefaultOAuth2ServiceFactoryWithProfileFormat[Auth0ProfileResponse](Auth0Profile)
    }
  }
}

class DefaultOAuth2ServiceFactory extends OAuth2ServiceFactory {
  def create(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    DefaultOAuth2ServiceFactory.defaultService(configuration, allCategories)
}

object DefaultOAuth2ServiceFactory extends {
  def apply(): DefaultOAuth2ServiceFactory = new DefaultOAuth2ServiceFactory

  def defaultService(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    DefaultOAuth2ServiceFactoryWithProfileFormat(configuration, allCategories).defaultService(configuration, allCategories)

  def service(configuration: OAuth2Configuration, allCategories: List[String])(implicit backend: SttpBackend[Future, Nothing, NothingT]): OAuth2Service =
    DefaultOAuth2ServiceFactoryWithProfileFormat(configuration, allCategories).service(configuration, allCategories)
}
