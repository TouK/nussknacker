package pl.touk.nussknacker.ui.security.oauth2

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RulesSet}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Profile.getUserRoles

@JsonCodec case class DefaultProfileResponse(sub: String, email: Option[String], username: Option[String])

object DefaultProfile extends OAuth2Profile[DefaultProfileResponse] {
  def getLoggedUser(profile: DefaultProfileResponse, configuration: OAuth2Configuration, allCategories: List[String]): LoggedUser = {
    val userRoles = getUserRoles(profile.email, configuration)
    val rulesSet = RulesSet.getOnlyMatchingRules(userRoles, configuration.rules, allCategories)
    val username = profile.username.getOrElse(profile.sub)
    LoggedUser(id = profile.sub, username = username, rulesSet = rulesSet)
  }
}
