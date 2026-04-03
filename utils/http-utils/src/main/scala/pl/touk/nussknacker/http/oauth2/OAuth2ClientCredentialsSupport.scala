package pl.touk.nussknacker.http.oauth2

import com.typesafe.scalalogging.LazyLogging
import eu.timepit.refined.types.string.NonEmptyString
import org.polyvariant.sttp.oauth2.{AccessTokenProvider, Secret, SttpOauth2ClientCredentialsBackend}
import org.polyvariant.sttp.oauth2.cache.future.FutureCachingAccessTokenProvider
import org.polyvariant.sttp.oauth2.common.Scope
import sttp.client3.{Request, Response, SttpBackend}
import sttp.model.{StatusCode, Uri}

import java.util.concurrent.ConcurrentHashMap
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class OAuth2ClientCredentialsSupport extends LazyLogging {

  private val oauth2AccessTokenProviderCache =
    new ConcurrentHashMap[OAuth2ClientCredentialsAuthorization, OAuth2AccessTokenProviderConfig]()

  def sendRequestWithAuthorization[T](
      httpClient: SttpBackend[Future, Any],
      request: Request[T, Any],
      oauth2Authorization: OAuth2ClientCredentialsAuthorization
  )(implicit ec: ExecutionContext): Either[OAuth2ClientCredentialsAuthorizationError, Future[Response[T]]] =
    oauth2AccessTokenProvider(httpClient, oauth2Authorization).map { accessTokenProviderConfig =>
      sendOauth2RequestWithRetry(httpClient, request, oauth2Authorization, accessTokenProviderConfig)
    }

  private def sendOauth2RequestWithRetry[T](
      httpClient: SttpBackend[Future, Any],
      request: Request[T, Any],
      oauth2Authorization: OAuth2ClientCredentialsAuthorization,
      accessTokenProviderConfig: OAuth2AccessTokenProviderConfig
  )(implicit ec: ExecutionContext): Future[Response[T]] = {
    def oauth2Backend(config: OAuth2AccessTokenProviderConfig) =
      SttpOauth2ClientCredentialsBackend(config.accessTokenProvider)(config.scope)(httpClient)

    oauth2Backend(accessTokenProviderConfig).send(request).flatMap { response =>
      // 401 can happen when a cached token expires or gets revoked between requests.
      // We invalidate provider cache and retry once with a fresh token to recover from this case, and avoid retry loops for persistent authorization problems.
      if (response.code == StatusCode.Unauthorized) {
        logger.warn(
          s"OAuth2 request returned 401. Invalidating token provider cache and retrying. ${oauth2LogContext(oauth2Authorization)}"
        )
        oauth2AccessTokenProviderCache.remove(oauth2Authorization)
        oauth2AccessTokenProvider(httpClient, oauth2Authorization) match {
          case Left(err) =>
            logger.warn(
              s"OAuth2 request retry failed after initial 401. ${oauth2LogContext(oauth2Authorization)}",
              err
            )
            Future.failed(err)
          case Right(retriedProviderConfig) =>
            oauth2Backend(retriedProviderConfig)
              .send(request)
              .map { retriedResponse =>
                if (retriedResponse.code == StatusCode.Unauthorized) {
                  logger.warn(
                    s"OAuth2 request still returned 401 after single retry. ${oauth2LogContext(oauth2Authorization)}"
                  )
                }
                retriedResponse
              }
              .recoverWith { case ex =>
                logger.warn(
                  s"OAuth2 request failed during retry after initial 401. ${oauth2LogContext(oauth2Authorization)}",
                  ex
                )
                Future.failed(ex)
              }
        }
      } else {
        Future.successful(response)
      }
    }
  }

  private def oauth2AccessTokenProvider(
      httpClient: SttpBackend[Future, Any],
      oauth2Authorization: OAuth2ClientCredentialsAuthorization
  )(implicit ec: ExecutionContext): Either[OAuth2ClientCredentialsAuthorizationError, OAuth2AccessTokenProviderConfig] =
    Option(oauth2AccessTokenProviderCache.get(oauth2Authorization)) match {
      case Some(cachedProviderConfig) => Right(cachedProviderConfig)
      case None =>
        createOauth2AccessTokenProvider(httpClient, oauth2Authorization).map { createdProviderConfig =>
          Option(oauth2AccessTokenProviderCache.putIfAbsent(oauth2Authorization, createdProviderConfig))
            .getOrElse(createdProviderConfig)
        }
    }

  private def createOauth2AccessTokenProvider(
      httpClient: SttpBackend[Future, Any],
      oauth2Authorization: OAuth2ClientCredentialsAuthorization
  )(
      implicit ec: ExecutionContext
  ): Either[OAuth2ClientCredentialsAuthorizationError, OAuth2AccessTokenProviderConfig] = {
    import org.polyvariant.sttp.oauth2.json.circe.instances._

    for {
      tokenUrl     <- parseTokenUrl(oauth2Authorization.tokenUrl)
      clientId     <- parseClientId(oauth2Authorization.clientId)
      clientSecret <- parseClientSecret(oauth2Authorization.clientSecret)
      scope        <- parseScope(oauth2Authorization.scope)
    } yield {
      val tokenProvider  = AccessTokenProvider[Future](tokenUrl, clientId, clientSecret)(httpClient)
      val cachedProvider = FutureCachingAccessTokenProvider.monixCacheInstance(tokenProvider)
      OAuth2AccessTokenProviderConfig(cachedProvider, scope)
    }
  }

  private def parseTokenUrl(tokenUrl: String): Either[OAuth2ClientCredentialsAuthorizationError, Uri] =
    Uri.parse(tokenUrl) match {
      case Left(error) =>
        Left(
          InvalidOAuth2ClientCredentialsConfiguration(
            tokenUrl,
            s"Invalid token URL: [$error]"
          )
        )
      case Right(url) => Right(url)
    }

  private def parseClientId(clientId: String): Either[OAuth2ClientCredentialsAuthorizationError, NonEmptyString] = {
    val trimmed = Option(clientId).map(_.trim).getOrElse("")
    if (trimmed.nonEmpty) {
      Right(NonEmptyString.unsafeFrom(trimmed))
    } else {
      Left(InvalidOAuth2ClientCredentialsConfiguration(String.valueOf(clientId), "Client ID cannot be empty"))
    }
  }

  private def parseClientSecret(
      clientSecret: String
  ): Either[OAuth2ClientCredentialsAuthorizationError, Secret[String]] =
    Option(clientSecret).filter(_.nonEmpty) match {
      case Some(nonEmptySecret) => Right(Secret(nonEmptySecret))
      case None =>
        Left(InvalidOAuth2ClientCredentialsConfiguration(String.valueOf(clientSecret), "Client Secret cannot be empty"))
    }

  private def parseScope(scope: Option[String]): Either[OAuth2ClientCredentialsAuthorizationError, Option[Scope]] =
    scope match {
      case Some(rawScope) =>
        val trimmedScope = Option(rawScope).map(_.trim).getOrElse("")
        if (trimmedScope.nonEmpty) {
          Try(Scope.unsafeFrom(trimmedScope)).toEither.left
            .map { error =>
              InvalidOAuth2ClientCredentialsConfiguration(rawScope, s"Invalid scope: [${error.getMessage}]")
            }
            .map(Some(_))
        } else {
          Left(InvalidOAuth2ClientCredentialsConfiguration(rawScope, "Scope cannot be empty"))
        }
      case None => Right(None)
    }

  private def oauth2LogContext(oauth2Authorization: OAuth2ClientCredentialsAuthorization): String =
    s"tokenUrl=[${oauth2Authorization.tokenUrl}], clientId=[${oauth2Authorization.clientId}]"

}

final case class OAuth2ClientCredentialsAuthorization(
    tokenUrl: String,
    clientId: String,
    clientSecret: String,
    scope: Option[String]
)

sealed abstract class OAuth2ClientCredentialsAuthorizationError(message: String)
    extends IllegalArgumentException(message)

final case class InvalidOAuth2ClientCredentialsConfiguration(value: String, reason: String)
    extends OAuth2ClientCredentialsAuthorizationError(
      s"Invalid OAuth2 client credentials configuration for value [$value]: $reason"
    )

private final case class OAuth2AccessTokenProviderConfig(
    accessTokenProvider: AccessTokenProvider[Future],
    scope: Option[Scope]
)
