package pl.touk.nussknacker.ui.security.ouath2

import java.net.URI

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.security.AuthenticationBackend
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.api.{GlobalPermission, LoggedUser, Permission}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2ServiceFactory.{OAuth2AuthenticateData, OAuth2Profile}
import pl.touk.nussknacker.ui.security.oauth2.{OAuth2ClientApi, OAuth2Configuration, OAuth2Service}
import pl.touk.nussknacker.ui.security.ouath2.OAuth2TestServiceFactory.{TestAccessTokenResponse, TestProfileResponse}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestOAuth2Service(clientApi: OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse], configuration: OAuth2Configuration) extends OAuth2Service with LazyLogging {
  override def authenticate(code: String): Future[OAuth2AuthenticateData] = {
    clientApi.accessTokenRequest(code).map{ resp =>
      OAuth2AuthenticateData(
        access_token = resp.access_token,
        token_type = resp.token_type,
        refresh_token = Option.empty
      )
    }
  }

  override def profile(token: String): Future[OAuth2Profile] = {
    clientApi.profileRequest(token).map{ prf =>
      OAuth2Profile(
        id = prf.uid,
        email = prf.email,
        isAdmin = OAuth2TestServiceFactory.isAdmin(prf.clearance.roles),
        permissions = OAuth2TestServiceFactory.getPermissions(prf.clearance.roles, prf.clearance.portals),
        accesses = OAuth2TestServiceFactory.getGlobalPermissions(prf.clearance.roles),
        roles = prf.clearance.roles
      )
    }
  }
}

object OAuth2TestServiceFactory {
  import cats.instances.all._
  import cats.syntax.semigroup._

  def getPermissions(roles: List[String], portals: List[String]): Map[String, Set[Permission]] = {
    val matched = if (isAdmin(roles)) Permission.ALL_PERMISSIONS else getOnlyMatchingRoles(roles).toSet
    portals.map(_ -> matched).map(List(_).toMap).foldLeft(Map.empty[String, Set[Permission]])(_ |+| _)
  }

  def isAdmin(roles: List[String]): Boolean =
    roles.contains(TestPermissionResponse.Admin.toString)

  def hasAccessAdminTab(roles: List[String]): Boolean =
    roles.contains(TestPermissionResponse.Admin.toString)

  def getOnlyMatchingRoles(roles: List[String]): List[Permission.Value] =
    roles
      .flatMap(p => TestPermissionResponse.mappedPermission.get(p))

  def getGlobalPermissions(roles: List[String]): List[GlobalPermission] = {
    if (isAdmin(roles)) {
      GlobalPermission.ALL_PERMISSIONS.toList
    } else if (hasAccessAdminTab(roles)) {
      List.apply(GlobalPermission.AdminTab)
    } else {
      List.empty
    }
  }

  def apply(configuration: OAuth2Configuration)(implicit backend: SttpBackend[Future, Nothing, NothingT]): TestOAuth2Service
    = new TestOAuth2Service(getTestClient(configuration), configuration)

  def getTestClient(configuration: OAuth2Configuration)(implicit backend: SttpBackend[Future, Nothing, NothingT]): OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse]
    = new OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse](configuration)

  def getTestConfig: OAuth2Configuration =
    OAuth2Configuration(
      AuthenticationBackend.OAuth2,
      "ui/server/develConf/tests/oauth2-users.conf",
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

    val mappedPermission = Map(
      Reader.toString -> Permission.Read,
      Writer.toString -> Permission.Write,
      Deployer.toString -> Permission.Deploy
    )
  }

  @JsonCodec case class TestAccessTokenResponse(access_token: String, token_type: String)
  @JsonCodec case class TestProfileResponse(email: String, uid: String, clearance: TestProfileClearanceResponse)
  @JsonCodec case class TestProfileClearanceResponse(roles: List[String], portals: List[String])
}