package pl.touk.nussknacker.ui.server

import akka.http.scaladsl.server.Route
import cats.effect.{IO, Resource}
import pl.touk.nussknacker.engine.ConfigWithUnresolvedVersion

trait RouteProvider[R <: Route] {

  def createRoute(config: ConfigWithUnresolvedVersion): Resource[IO, R]
}
