package pl.touk.nussknacker.ui.security.oauth2

import java.io.{File, FileInputStream}
import java.net.URI
import java.security.KeyFactory
import java.security.interfaces.RSAPublicKey
import java.security.spec.X509EncodedKeySpec
import java.security.{KeyFactory, PublicKey}
import java.util.Base64

import org.apache.commons.io.IOUtils
import com.typesafe.config.Config
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration
import pl.touk.nussknacker.ui.security.api.AuthenticationMethod.AuthenticationMethod
import ProfileFormat.ProfileFormat
import pl.touk.nussknacker.ui.security.CertificatesAndKeys



case class OAuth2Configuration(method: AuthenticationMethod,
                               usersFile: String,
                               authorizeUri: URI,
                               clientSecret: String,
                               clientId: String,
                               profileUri: URI,
                               profileFormat: Option[ProfileFormat],
                               accessTokenUri: URI,
                               redirectUri: URI,
                               implicitGrantEnabled: Boolean,
                               jwt: Option[JwtConfiguration],
                               accessTokenParams: Map[String, String] = Map.empty,
                               authorizeParams: Map[String, String] = Map.empty,
                               headers: Map[String, String] = Map.empty,
                               authorizationHeader: String = "Authorization"
                              ) extends AuthenticationConfiguration {

  override def authorizeUrl: Option[URI] = Option({
    new URI(dispatch.url(authorizeUri.toString)
      .setQueryParameters((Map(
        "client_id" -> clientId,
        "redirect_uri" -> redirectUrl
      ) ++ authorizeParams).mapValues(v => Seq(v)))
      .url)
  })

  def redirectUrl: String = redirectUri.toString
}

object OAuth2Configuration {
  import AuthenticationConfiguration._
  import JwtConfiguration.jwtConfigurationVR
  import pl.touk.nussknacker.engine.util.config.FicusReaders._
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  def create(config: Config): OAuth2Configuration = config.as[OAuth2Configuration](authenticationConfigPath)
}

object ProfileFormat extends Enumeration {
  type ProfileFormat = Value
  val GITHUB = Value("github")
  val AUTH0 = Value("auth0")
}

trait JwtConfiguration {
  def authServerPublicKey: PublicKey
}

object JwtConfiguration {
  import net.ceedubs.ficus.readers.ValueReader
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

  implicit val jwtConfigurationVR: ValueReader[JwtConfiguration] = ValueReader.relative(_.rootAs[JwtConfig])

  val base64Decoder: Base64.Decoder = Base64.getDecoder

  private case class JwtConfig(publicKey: Option[String],
                               publicKeyFile: Option[String],
                               certificate: Option[String],
                               certificateFile: Option[String]) extends JwtConfiguration {
    def authServerPublicKey: PublicKey = {
      val charset: String = "UTF-8"

      def getContent(content: Option[String], file: Option[String]): Option[String] =
        content.orElse(file map { path =>
          IOUtils.toString(new FileInputStream(new File(path)), charset)
        })

      getContent(publicKey, publicKeyFile).map(CertificatesAndKeys.publicKeyFromString(_, charset)) orElse
        getContent(certificate, certificateFile).map(CertificatesAndKeys.publicKeyFromStringCertificate(_, charset)) getOrElse {
        throw new Exception("one of the: 'publicKey', 'publicKeyFile', 'certificate', 'certificateFile' fields should be provided in the authentication.jwt configuration")
      }
    }
  }
}
