package pl.touk.nussknacker.ui.security.oauth2

import java.net.URI
import java.nio.charset.{Charset, StandardCharsets}
import java.security.PublicKey
import com.typesafe.config.Config
import pl.touk.nussknacker.engine.util.Implicits.SourceIsReleasable
import pl.touk.nussknacker.ui.security.CertificatesAndKeys
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration
import pl.touk.nussknacker.ui.security.oauth2.ProfileFormat.{OIDC, ProfileFormat}
import sttp.model.{HeaderNames, MediaType, Uri}

import scala.concurrent.duration.{FiniteDuration, HOURS}
import scala.io.Source
import scala.util.Using

case class OAuth2Configuration(usersFile: URI,
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
                               defaultTokenExpirationTime: FiniteDuration = FiniteDuration(1, HOURS),
                               anonymousUserRole: Option[String] = None
                              ) extends AuthenticationConfiguration {
  override def name: String = OAuth2Configuration.name

  def authorizeUrl: Option[URI] = Option(
    Uri(authorizeUri)
      .param("client_id", clientId)
      .param("redirect_uri", redirectUri.map(_.toString))
      .params(authorizeParams))
    .map(_.toJavaUri)

  def authSeverPublicKey: Option[PublicKey] = Option.empty

  def idTokenNonceVerificationRequired: Boolean = jwt.exists(_.idTokenNonceVerificationRequired)
}

object OAuth2Configuration {

  import AuthenticationConfiguration._
  import JwtConfiguration.jwtConfigurationVR
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  val name = "OAuth2"
  def create(config: Config): OAuth2Configuration = config.as[OAuth2Configuration](authenticationConfigPath)
}

object ProfileFormat extends Enumeration {
  type ProfileFormat = Value
  val GITHUB = Value("github")
  val OIDC = Value("oidc")
}

trait JwtConfiguration {
  def accessTokenIsJwt: Boolean

  def userinfoFromIdToken: Boolean

  def authServerPublicKey: PublicKey

  def idTokenNonceVerificationRequired: Boolean

  def audience: Option[String]
}

object JwtConfiguration {

  import net.ceedubs.ficus.readers.ValueReader
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  implicit val jwtConfigurationVR: ValueReader[JwtConfiguration] = ValueReader.relative(_.rootAs[JwtConfig])

  private case class JwtConfig(accessTokenIsJwt: Boolean = false,
                               userinfoFromIdToken: Boolean = false,
                               audience: Option[String],
                               publicKey: Option[String],
                               publicKeyFile: Option[String],
                               certificate: Option[String],
                               certificateFile: Option[String],
                               idTokenNonceVerificationRequired: Boolean = false) extends JwtConfiguration {
    def authServerPublicKey: PublicKey = {
      val charset: Charset = StandardCharsets.UTF_8

      def getContent(content: Option[String], file: Option[String]): Option[String] =
        content.orElse(file map { path =>
          Using.resource(Source.fromFile(path, StandardCharsets.UTF_8.name))(_.mkString)
        })

      getContent(publicKey, publicKeyFile).map(CertificatesAndKeys.publicKeyFromString(_, charset)) orElse
        getContent(certificate, certificateFile).map(CertificatesAndKeys.publicKeyFromStringCertificate(_, charset)) getOrElse {
        throw new Exception("one of the: 'publicKey', 'publicKeyFile', 'certificate', 'certificateFile' fields should be provided in the authentication.jwt configuration")
      }
    }
  }
}
