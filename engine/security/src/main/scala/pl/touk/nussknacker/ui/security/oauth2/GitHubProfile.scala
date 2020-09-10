package pl.touk.nussknacker.ui.security.oauth2

import io.circe.generic.JsonCodec
import pl.touk.nussknacker.ui.security.api.{LoggedUser, RulesSet}
import pl.touk.nussknacker.ui.security.oauth2.OAuth2Profile.getUserRoles

@JsonCodec case class GitHubProfileResponse(id: Long, email: Option[String], login: Option[String])

object GitHubProfile extends OAuth2Profile[GitHubProfileResponse] {
  def getLoggedUser(profile: GitHubProfileResponse, configuration: OAuth2Configuration, allCategories: List[String]): LoggedUser = {
    val userRoles = getUserRoles(profile.email, configuration)
    val rulesSet = RulesSet.getOnlyMatchingRules(userRoles, configuration.rules, allCategories)
    val username = profile.login.getOrElse(profile.id.toString)
    LoggedUser(id = profile.id.toString, username = username, rulesSet = rulesSet)
  }
}
