package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.server.Route

trait RouteWithoutUser {
  def route(): Route
}
