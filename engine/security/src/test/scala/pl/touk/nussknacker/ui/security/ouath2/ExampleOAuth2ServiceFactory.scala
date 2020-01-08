package pl.touk.nussknacker.ui.security.ouath2

import java.net.URI

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.api.{AuthenticationMethod, GlobalPermission, LoggedUser, Permission}
import pl.touk.nussknacker.ui.security.oauth2.{OAuth2AuthenticateData, OAuth2ClientApi, OAuth2Configuration, OAuth2Service, OAuth2ServiceFactory}
import pl.touk.nussknacker.ui.security.ouath2.ExampleOAuth2ServiceFactory.{TestAccessTokenResponse, TestProfileResponse}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

class ExampleOAuth2Service(clientApi: OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse], configuration: OAuth2Configuration) extends OAuth2Service with LazyLogging {
  override def authenticate(code: String): Future[OAuth2AuthenticateData] = {
    clientApi.accessTokenRequest(code).map{ resp =>
      OAuth2AuthenticateData(
        access_token = resp.access_token,
        token_type = resp.token_type,
        refresh_token = Option.empty
      )
    }
  }

  override def authorize(token: String): Future[LoggedUser] = {
    clientApi.profileRequest(token).map{ prf =>
      LoggedUser(
        id = prf.uid,
        username = prf.email,
        isAdmin = ExampleOAuth2ServiceFactory.isAdmin(prf.clearance.roles),
        categoryPermissions = ExampleOAuth2ServiceFactory.getPermissions(prf.clearance.roles, prf.clearance.portals),
        globalPermissions = ExampleOAuth2ServiceFactory.getGlobalPermissions(prf.clearance.roles)
      )
    }
  }
}

class ExampleOAuth2ServiceFactory extends OAuth2ServiceFactory {
  override def create(configuration: OAuth2Configuration, allCategories: List[String]): OAuth2Service =
    ExampleOAuth2ServiceFactory.defaultService(configuration)
}

object ExampleOAuth2ServiceFactory {
  import cats.instances.all._
  import cats.syntax.semigroup._

  def getPermissions(roles: List[String], portals: List[String]): Map[String, Set[Permission]] = {
    val matched = if (isAdmin(roles)) Permission.ALL_PERMISSIONS else getOnlyMatchingRoles(roles).toSet
    portals.map(_ -> matched).map(List(_).toMap).foldLeft(Map.empty[String, Set[Permission]])(_ |+| _)
  }

  def isAdmin(roles: List[String]): Boolean =
    roles.contains(TestPermissionResponse.Admin.toString)

  def hasAccessAdminTab(roles: List[String]): Boolean =
    roles.contains(TestPermissionResponse.AdminTab.toString)

  def getOnlyMatchingRoles(roles: List[String]): List[Permission.Value] =
    roles.flatMap(TestPermissionResponse.mapToNkPermission)

  def getGlobalPermissions(roles: List[String]): List[GlobalPermission] = {
    if (isAdmin(roles)) {
      GlobalPermission.ALL_PERMISSIONS.toList
    } else if (hasAccessAdminTab(roles)) {
      List.apply(GlobalPermission.AdminTab)
    } else {
      List.empty
    }
  }

  def apply(): ExampleOAuth2ServiceFactory = new ExampleOAuth2ServiceFactory()

  def defaultService(configuration: OAuth2Configuration): ExampleOAuth2Service =
    new ExampleOAuth2Service(OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse](configuration), configuration)

  def service(configuration: OAuth2Configuration)(implicit backend: SttpBackend[Future, Nothing, NothingT]): ExampleOAuth2Service =
    new ExampleOAuth2Service(testClient(configuration), configuration)

  def testClient(configuration: OAuth2Configuration)(implicit backend: SttpBackend[Future, Nothing, NothingT]): OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse]
    = new OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse](configuration)

  def testConfig: OAuth2Configuration =
    OAuth2Configuration(
      AuthenticationMethod.OAuth2,
      "ui/server/src/test/resources/oauth2-users.conf",
      URI.create("https://github.com/login/oauth/authorize"),
      "clientSecret",
      "clientId",
      URI.create("https://api.github.com/user"),
      URI.create("https://github.com/login/oauth/access_token"),
      URI.create("http://demo.nussknacker.pl")
    )

  object TestPermissionResponse extends Enumeration {
    type PermissionTest = Value
    val Reader = Value("Reader")
    val Writer = Value("Writer")
    val Deployer = Value("Deployer")
    val Admin = Value("Admin")
    val AdminTab = Value("AdminTab")

    val mappedPermissions = Map(
      Reader.toString -> Permission.Read,
      Writer.toString -> Permission.Write,
      Deployer.toString -> Permission.Deploy
    )

    def mapToNkPermission(role: String): Option[Permission] =
      mappedPermissions.get(role)
  }

  @JsonCodec case class TestAccessTokenResponse(access_token: String, token_type: String)
  @JsonCodec case class TestProfileResponse(email: String, uid: String, clearance: TestProfileClearanceResponse)
  @JsonCodec case class TestProfileClearanceResponse(roles: List[String], portals: List[String])
}