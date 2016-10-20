package pl.touk.esp.ui.api

import akka.http.scaladsl.server.Directives
import argonaut.{EncodeJson, Json, PrettyParams}
import com.typesafe.config.Config
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader._
import pl.touk.esp.ui.security.LoggedUser
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.ExecutionContext

class SettingsResources(config: Config)(implicit ec: ExecutionContext)
  extends Directives with Argonaut62Support {

  import argonaut.ArgonautShapeless._

  implicit val printer: Json => String =
    PrettyParams.spaces2.copy(dropNullKeys = true, preserveOrder = true).pretty

  implicit val grafanaEncode = EncodeJson.of[GrafanaSettings]

  val route = (user: LoggedUser) =>
    pathPrefix("settings") {
      path("grafana") {
        get {
          complete {
            config.as[GrafanaSettings]("grafanaSettings")
          }
        }
      }
    }
}

case class GrafanaSettings(url: String, dashboard: String, hosts: String)
