package pl.touk.nussknacker.ui.security.oauth2

import java.io.File
import java.net.URI

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.{AuthenticationBackend, AuthenticationConfiguration, AuthenticationConfigurationFactory}

final case class OAuth2Configuration(backend: AuthenticationBackend.Value,
                               usersFile: String,
                               authorizeUri: URI,
                               clientSecret: String,
                               clientId: String,
                               profileUri: URI,
                               accessTokenUri: URI,
                               redirectUri: URI,
                               accessTokenParams: Map[String, String] = Map.empty,
                               authorizeParams: Map[String, String] = Map.empty,
                               headers: Map[String, String] = Map.empty,
                               authorizationHeader: String = "Authorization"
                         ) extends AuthenticationConfiguration {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  private val userConfig: Config = ConfigFactory.parseFile(new File(usersFile))

  lazy val users: List[OAuth2ConfigUser]
    = userConfig.as[List[OAuth2ConfigUser]](AuthenticationConfigurationFactory.usersConfigurationPath)

  lazy val rules: List[OAuth2ConfigRule]
    = userConfig.as[List[OAuth2ConfigRule]](AuthenticationConfigurationFactory.rulesConfigurationPath)

  override def authorizeUrl: Option[URI] = Option.apply({
    new URI(dispatch.url(authorizeUri.toString)
      .setQueryParameters((Map(
        "client_id" -> clientId,
        "redirect_uri" -> redirectUrl
      ) ++ authorizeParams).mapValues(v => Seq(v)))
      .url)
  })

  def redirectUrl: String = redirectUri.toString
}

final case class OAuth2ConfigUser(email: String, roles: List[String])

final case class OAuth2ConfigRule(roleName: String,
                                  isAdmin: Boolean = false,
                                  categories: List[String] = List.empty,
                                  permissions: List[Permission] = List.empty,
                                  globalPermissions: List[GlobalPermission] = List.empty)