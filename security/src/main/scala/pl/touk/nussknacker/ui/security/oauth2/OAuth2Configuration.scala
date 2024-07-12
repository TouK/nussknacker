package pl.touk.nussknacker.ui.security.oauth2

import com.typesafe.config.Config
import net.ceedubs.ficus.readers.ValueReader
import pl.touk.nussknacker.engine.util.config.FicusReaders.forDecoder
import pl.touk.nussknacker.ui.security.CertificatesAndKeys
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration.ConfigUser
import pl.touk.nussknacker.ui.security.api.{AuthenticationConfiguration, FrontendStrategySettings}
import pl.touk.nussknacker.ui.security.oauth2.ProfileFormat.ProfileFormat
import pl.touk.nussknacker.ui.security.oauth2.UsernameClaim.UsernameClaim
import sttp.model.{HeaderNames, MediaType, Uri}

import java.net.URI
import java.nio.charset.{Charset, StandardCharsets}
import java.security.PublicKey
import scala.concurrent.duration.{FiniteDuration, HOURS}
import scala.io.Source
import scala.util.Using

final case class OAuth2Configuration(
    usersFile: URI,
    authorizeUri: URI,
    clientSecret: String,
    clientId: String,
    profileUri: URI,
    profileFormat: Option[ProfileFormat],
    accessTokenUri: URI,
    redirectUri: Option[URI],
    implicitGrantEnabled: Boolean = false,
    jwt: Option[JwtConfiguration],
    accessTokenParams: Map[String, String] = Map.empty,
    authorizeParams: Map[String, String] = Map.empty,
    headers: Map[String, String] = Map.empty,
    authorizationHeader: String = HeaderNames.Authorization,
    accessTokenRequestContentType: String = MediaType.ApplicationJson.toString(),
    defaultTokenExpirationDuration: FiniteDuration = FiniteDuration(1, HOURS),
    anonymousUserRole: Option[String] = None,
    isAdminImpersonationPossible: Boolean = false,
    tokenCookie: Option[TokenCookieConfig] = None,
    overrideFrontendAuthenticationStrategy: Option[FrontendStrategySettings] = None,
    usernameClaim: Option[UsernameClaim] = None,
    realm: Option[String] = None
) extends AuthenticationConfiguration {
  override def name: String = OAuth2Configuration.name

  override lazy val users: List[ConfigUser] = usersOpt.getOrElse(Nil)

  def getUserRoles(identity: String): Set[String] =
    findUserById(identity)
      .map(_.roles)
      .getOrElse(Set.empty)

  def findUserById(id: String): Option[ConfigUser] = users.find(_.identity == id)

  def authorizeUrl: Option[URI] = Option(
    Uri(authorizeUri)
      .addParam("client_id", clientId)
      .addParam("redirect_uri", redirectUri.map(_.toString))
      .addParams(authorizeParams)
  )
    .map(_.toJavaUri)

  def authSeverPublicKey: Option[PublicKey] = Option.empty

  def idTokenNonceVerificationRequired: Boolean = jwt.exists(_.idTokenNonceVerificationRequired)
}

object OAuth2Configuration {

  import AuthenticationConfiguration._
  import JwtConfiguration.jwtConfigurationVR
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  private implicit val valueReader: ValueReader[FrontendStrategySettings] = forDecoder

  val name                                        = "OAuth2"
  def create(config: Config): OAuth2Configuration = config.as[OAuth2Configuration](authenticationConfigPath)
}

final case class TokenCookieConfig(name: String, path: Option[String], domain: Option[String])

object ProfileFormat extends Enumeration {
  type ProfileFormat = Value
  val GITHUB = Value("github")
  val OIDC   = Value("oidc")
}

object UsernameClaim extends Enumeration {
  type UsernameClaim = Value
  val PreferredUsername: UsernameClaim = Value("preferred_username")
  val GivenName: UsernameClaim         = Value("given_name")
  val Nickname: UsernameClaim          = Value("nickname")
  val Name: UsernameClaim              = Value("name")
}

trait JwtConfiguration {
  def accessTokenIsJwt: Boolean

  def userinfoFromIdToken: Boolean

  def authServerPublicKey: Option[PublicKey]

  def idTokenNonceVerificationRequired: Boolean

  def audience: Option[String]
}

object JwtConfiguration {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.ValueReader
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  implicit val jwtConfigurationVR: ValueReader[JwtConfiguration] = ValueReader.relative(_.rootAs[JwtConfig])

  private final case class JwtConfig(
      accessTokenIsJwt: Boolean = false,
      userinfoFromIdToken: Boolean = false,
      audience: Option[String],
      publicKey: Option[String],
      publicKeyFile: Option[String],
      certificate: Option[String],
      certificateFile: Option[String],
      idTokenNonceVerificationRequired: Boolean = false
  ) extends JwtConfiguration {

    def authServerPublicKey: Some[PublicKey] = {
      val charset: Charset = StandardCharsets.UTF_8

      def getContent(content: Option[String], file: Option[String]): Option[String] =
        content.orElse(file map { path =>
          Using.resource(Source.fromFile(path, StandardCharsets.UTF_8.name))(_.mkString)
        })

      getContent(publicKey, publicKeyFile).map(CertificatesAndKeys.publicKeyFromString(_, charset)) orElse
        getContent(certificate, certificateFile).map(
          CertificatesAndKeys.publicKeyFromStringCertificate(_, charset)
        ) match {
        case x @ Some(_) => x
        case _ =>
          throw new Exception(
            "one of the: 'publicKey', 'publicKeyFile', 'certificate', 'certificateFile' fields should be provided in the authentication.jwt configuration"
          )
      }
    }

  }

}
