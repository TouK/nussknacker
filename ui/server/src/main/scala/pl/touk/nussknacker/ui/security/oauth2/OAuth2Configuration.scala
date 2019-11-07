package pl.touk.nussknacker.ui.security.oauth2

import java.io.File
import java.net.URI

import com.typesafe.config.{Config, ConfigFactory}
import pl.touk.nussknacker.ui.security.api.AuthenticationConfiguration
import pl.touk.nussknacker.ui.security.api.AuthenticationMethod.AuthenticationMethod
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Configuration.{OAuth2ConfigRule, OAuth2ConfigUser}

case class OAuth2Configuration(method: AuthenticationMethod,
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

  private val userConfig: Config = ConfigFactory.parseFile(new File(usersFile))

  lazy val users: List[OAuth2ConfigUser] = OAuth2Configuration.getUsers(userConfig)

  lazy val rules: List[OAuth2ConfigRule] = OAuth2Configuration.getRules(userConfig)

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
  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.readers.EnumerationReader._

  def create(config: Config): OAuth2Configuration = config.as[OAuth2Configuration](authenticationConfigPath)

  def getUsers(config: Config): List[OAuth2ConfigUser] =
    config.as[List[OAuth2ConfigUser]](AuthenticationConfiguration.usersConfigurationPath)

  def getRules(config: Config): List[OAuth2ConfigRule] =
    config.as[List[OAuth2ConfigRule]](AuthenticationConfiguration.rulesConfigurationPath)

  case class OAuth2ConfigUser(email: String, roles: List[String])

  case class OAuth2ConfigRule(role: String,
                              isAdmin: Boolean = false,
                              categories: List[String] = List.empty,
                              permissions: List[Permission] = List.empty,
                              globalPermissions: List[GlobalPermission] = List.empty)
}