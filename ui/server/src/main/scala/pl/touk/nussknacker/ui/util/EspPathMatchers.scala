package pl.touk.nussknacker.ui.util

import akka.http.scaladsl.server.{PathMatcher1, PathMatchers}
import pl.touk.nussknacker.engine.api.process.VersionId

import scala.util.Try

trait EspPathMatchers extends PathMatchers {

  def EnumSegment[T<:Enumeration](enumCompanion:T) : PathMatcher1[enumCompanion.Value] =
    Segment.flatMap(value => Try(enumCompanion.withName(value)).toOption)

  def VersionIdSegment: PathMatcher1[VersionId] =
    Segment.flatMap(value => Try(value.toLong).map(VersionId(_)).toOption)

}
