package pl.touk.nussknacker.engine.flink.table

import com.typesafe.scalalogging.LazyLogging

import scala.io.Source
import scala.util.{Failure, Success, Try}

object SqlFromResourceReader extends LazyLogging {
  private val separator = ";\\s*"

  type SqlStatement = String

  def readFileFromResources(resourceName: String): List[SqlStatement] =
    Try {
      val source = Source.fromFile(resourceName)
      try {
        source.mkString.split(separator).toList
      } finally {
        source.close()
      }
    } match {
      case Failure(exception) =>
        logger.warn(s"Couldn't parse sql tables definition: $exception")
        List.empty
      case Success(value) => value
    }

}
