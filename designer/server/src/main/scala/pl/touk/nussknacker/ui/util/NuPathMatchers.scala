package pl.touk.nussknacker.ui.util

import org.apache.pekko.http.scaladsl.server.{PathMatcher1, PathMatchers}
import pl.touk.nussknacker.engine.api.process.{ProcessName, VersionId}

import scala.util.Try

trait NuPathMatchers extends PathMatchers {

  def ProcessNameSegment: PathMatcher1[ProcessName] =
    Segment.map(ProcessName(_))

  def VersionIdSegment: PathMatcher1[VersionId] =
    Segment.flatMap(value => Try(value.toLong).map(VersionId(_)).toOption)

}
