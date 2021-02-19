package pl.touk.nussknacker.ui.security.oauth2

import java.net.URI

import com.typesafe.scalalogging.LazyLogging
import io.circe.generic.JsonCodec
import io.circe.generic.extras.{Configuration, ConfiguredJsonCodec, JsonKey}
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.Permission.Permission
import pl.touk.nussknacker.ui.security.api.{AuthenticationMethod, GlobalPermission, LoggedUser, Permission}
import pl.touk.nussknacker.ui.security.oauth2.ExampleOAuth2ServiceFactory.{TestAccessTokenResponse, TestProfileResponse}
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.duration.{Deadline, FiniteDuration}
import scala.concurrent.{ExecutionContext, Future}


class ExampleOAuth2Service(clientApi: OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse], configuration: OAuth2Configuration)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]) extends OAuth2NewService[LoggedUser, OAuth2AuthorizationData] with LazyLogging {


  def obtainAuthorizationAndUserInfo(authorizationCode: String): Future[(OAuth2AuthorizationData, Option[LoggedUser])] =
    clientApi.accessTokenRequest(authorizationCode).map((_, None))

  def checkAuthorizationAndObtainUserinfo(accessToken: String): Future[(LoggedUser, Option[Deadline])] =
    clientApi.profileRequest(accessToken).map{ prf =>
      LoggedUser(
        id = prf.uid,
        username = prf.email,
        isAdmin = ExampleOAuth2ServiceFactory.isAdmin(prf.clearance.roles),
        categoryPermissions = ExampleOAuth2ServiceFactory.getPermissions(prf.clearance.roles, prf.clearance.portals),
        globalPermissions = ExampleOAuth2ServiceFactory.getGlobalPermissions(prf.clearance.roles)
      )
    }.map((_, None))
}

class ExampleOAuth2ServiceFactory extends OAuth2ServiceFactory {
  override def createNew(configuration: OAuth2Configuration, allCategories: List[String])(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): OAuth2NewService[LoggedUser, OAuth2AuthorizationData] =
    ExampleOAuth2ServiceFactory.service(configuration)
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

  def service(configuration: OAuth2Configuration)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Nothing, NothingT]): ExampleOAuth2Service =
    new ExampleOAuth2Service(testClient(configuration), configuration)

  def testClient(configuration: OAuth2Configuration)(implicit ec: ExecutionContext, backend: SttpBackend[Future, Nothing, NothingT]): OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse]
    = new OAuth2ClientApi[TestProfileResponse, TestAccessTokenResponse](configuration)

  def testConfig: OAuth2Configuration =
    OAuth2Configuration(
      AuthenticationMethod.OAuth2,
      "ui/server/src/test/resources/oauth2-users.conf",
      URI.create("https://github.com/login/oauth/authorize"),
      "clientSecret",
      "clientId",
      URI.create("https://api.github.com/user"),
      Some(ProfileFormat.GITHUB),
      URI.create("https://github.com/login/oauth/access_token"),
      URI.create("http://demo.nussknacker.pl"),
      false,
      None
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

  @ConfiguredJsonCodec case class TestAccessTokenResponse(@JsonKey("access_token") accessToken: String, @JsonKey("token_type") tokenType: String) extends OAuth2AuthorizationData {
    val expirationPeriod: Option[FiniteDuration] = None
    val refreshToken: Option[String] = None
  }

  object TestAccessTokenResponse extends CirceDurationConversions {
    implicit val config: Configuration = Configuration.default
  }

  @JsonCodec case class TestProfileResponse(email: String, uid: String, clearance: TestProfileClearanceResponse)
  @JsonCodec case class TestTokenIntrospectionResponse(exp: Option[Long])
  @JsonCodec case class TestProfileClearanceResponse(roles: List[String], portals: List[String])
}
