package pl.touk.nussknacker.ui.server

import cats.effect.{IO, Resource}
import org.apache.pekko.http.scaladsl.server.Route
import pl.touk.nussknacker.ui.config.DesignerConfig

trait RouteProvider[R <: Route] {

  def createRoute(config: DesignerConfig): Resource[IO, R]

}
