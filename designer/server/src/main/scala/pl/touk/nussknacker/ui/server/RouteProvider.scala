package pl.touk.nussknacker.ui.server

import org.apache.pekko.http.scaladsl.server.Route
import cats.effect.{IO, Resource}
import pl.touk.nussknacker.ui.config.DesignerConfig

trait RouteProvider[R <: Route] {

  def createRoute(config: DesignerConfig): Resource[IO, R]

}
