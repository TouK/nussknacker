package pl.touk.nussknacker.ui.util

import org.apache.pekko.http.scaladsl.server.Directive0

object WithDirectives {
  def apply(directives: Directive0*): Directive0 = directives.reduce((d1, d2) => d1.and(d2))
}
