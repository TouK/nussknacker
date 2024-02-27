package pl.touk.nussknacker.engine.flink.table

import com.typesafe.scalalogging.LazyLogging

import scala.util.{Failure, Success, Try}

object SqlFromResourceReader extends LazyLogging {
  private val separator = ";\\s*"

  type SqlStatement = String

  def readFileFromResources(resourceName: String): List[SqlStatement] =
    Try {
      scala.io.Source
        .fromResource(resourceName)
        .mkString
        .split(separator)
        .toList
    } match {
      case Failure(exception) =>
        logger.warn(s"Couldn't parse sql tables definition: $exception")
        List.empty
      case Success(value) => value
    }

}
