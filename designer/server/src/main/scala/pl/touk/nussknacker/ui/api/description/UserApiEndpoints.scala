package pl.touk.nussknacker.ui.api.description

import derevo.circe.{decoder, encoder}
import derevo.derive
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions
import pl.touk.nussknacker.restmodel.BaseEndpointDefinitions.SecuredEndpoint
import pl.touk.nussknacker.security.AuthCredentials
import pl.touk.nussknacker.ui.security.api.GlobalPermission.GlobalPermission
import pl.touk.nussknacker.ui.security.api.{AdminUser, CommonUser, LoggedUser}
import sttp.model.StatusCode.Ok
import sttp.tapir.EndpointIO.Example
import sttp.tapir.derevo.schema
import sttp.tapir.json.circe.jsonBody
import sttp.tapir.{EndpointInput, statusCode}

class UserApiEndpoints(auth: EndpointInput[AuthCredentials]) extends BaseEndpointDefinitions {

  lazy val userInfoEndpoint: SecuredEndpoint[Unit, Unit, DisplayableUser, Any] =
    baseNuApiEndpoint
      .summary("Logged user info service")
      .tag("User")
      .get
      .in("user")
      .out(
        statusCode(Ok).and(
          jsonBody[DisplayableUser]
            .example(
              Example.of(
                summary = Some("Common user info"),
                value = DisplayableUser(
                  id = "reader",
                  username = "reader",
                  isAdmin = false,
                  categories = List("Category1"),
                  categoryPermissions = Map("Category1" -> List("Read")),
                  globalPermissions = List.empty
                )
              )
            )
            .example(
              Example.of(
                summary = Some("Admin user info"),
                value = DisplayableUser(
                  id = "admin",
                  username = "admin",
                  isAdmin = true,
                  categories = List("Category1", "Category2"),
                  categoryPermissions = Map.empty,
                  globalPermissions = List.empty
                )
              )
            )
        )
      )
      .withSecurity(auth)

}

@derive(schema, encoder, decoder)
final case class DisplayableUser private (
    id: String,
    username: String,
    isAdmin: Boolean,
    categories: List[String],
    categoryPermissions: Map[String, List[String]],
    globalPermissions: List[GlobalPermission]
)

object DisplayableUser {
  import pl.touk.nussknacker.engine.util.Implicits._

  def apply(user: LoggedUser, allUserAccessibleCategories: Iterable[String]): DisplayableUser = user match {
    case CommonUser(id, username, categoryPermissions, globalPermissions) =>
      new DisplayableUser(
        id = id,
        isAdmin = false,
        username = username,
        // Sorting for stable tests results
        categories = allUserAccessibleCategories.toList.sorted,
        categoryPermissions = categoryPermissions.mapValuesNow(_.map(_.toString).toList.sorted),
        globalPermissions = globalPermissions
      )
    case AdminUser(id, username) =>
      new DisplayableUser(
        id = id,
        isAdmin = true,
        username = username,
        // Sorting for stable tests results
        categories = allUserAccessibleCategories.toList.sorted,
        categoryPermissions = Map.empty,
        globalPermissions = Nil
      )
  }

}
