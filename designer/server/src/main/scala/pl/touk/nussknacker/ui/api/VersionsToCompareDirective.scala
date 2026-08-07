package pl.touk.nussknacker.ui.api

import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server.{Directive, Directives}
import pl.touk.nussknacker.ui.process.VersionsWithDifferencesService

trait VersionsToCompareDirective {
  import Directives._

  // completed rather than rejected, so this does not depend on a rejection handler above the route
  protected def versionsToCompare: Directive[Tuple1[Int]] =
    parameter(Symbol("limit").as[Int].withDefault(VersionsWithDifferencesService.DefaultVersionsCompared)).flatMap {
      case limit if VersionsWithDifferencesService.isValidLimit(limit) => provide(limit)
      case _ =>
        Directive[Tuple1[Int]] { _ =>
          complete(
            StatusCodes.BadRequest,
            s"limit must be between ${VersionsWithDifferencesService.MinVersionsCompared} " +
              s"and ${VersionsWithDifferencesService.MaxVersionsCompared}"
          )
        }
    }

}
