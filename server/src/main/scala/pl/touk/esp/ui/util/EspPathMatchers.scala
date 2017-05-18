package pl.touk.esp.ui.util

import akka.http.scaladsl.server.{PathMatcher1, PathMatchers}

import scala.util.Try

trait EspPathMatchers extends PathMatchers {

  def EnumSegment[T<:Enumeration](enumCompanion:T) : PathMatcher1[enumCompanion.Value] =
    Segment.flatMap(value => Try(enumCompanion.withName(value)).toOption)

}
