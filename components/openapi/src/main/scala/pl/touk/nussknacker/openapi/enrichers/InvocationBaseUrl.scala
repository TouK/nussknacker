package pl.touk.nussknacker.openapi.enrichers

import java.net.{MalformedURLException, URL}

object InvocationBaseUrl {

  def determineInvocationBaseUrl(
      definitionUrl: URL,
      rootUrl: Option[URL],
      serversFromDefinition: List[String]
  ): URL = {
    def relativeToDefinitionUrl(serversUrlPart: String): URL = {
      try {
        new URL(definitionUrl, serversUrlPart)
      } catch {
        case _: MalformedURLException =>
          new URL(serversUrlPart)
      }
    }
    // Regarding https://spec.openapis.org/oas/v3.1.0#fixed-fields default server url is /
    rootUrl.getOrElse(relativeToDefinitionUrl(serversFromDefinition.headOption.getOrElse("/")))
  }

}
