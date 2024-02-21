package pl.touk.nussknacker.engine.flink.table.generic

import com.typesafe.scalalogging.LazyLogging
import scala.util.{Failure, Success, Try}

// TODO local: write tests for this
object DataSourceSqlStatementsReader extends LazyLogging {

  val defaultDataSourceDefinitionFileName: String = "tables-definition.sql"
  private val separator                           = ";\\s*"

  def readFileFromResources(resourceFileName: String = defaultDataSourceDefinitionFileName): List[String] =
    Try {
      scala.io.Source
        .fromResource(resourceFileName)
        .mkString
        .split(separator)
        .filter(_.trim.startsWith("CREATE TABLE"))
        .toList
    } match {
      // TODO local: handle file not existing
      case Failure(exception) =>
        logger.warn(s"Couldn't parse sql tables definition: $exception")
        List.empty
      case Success(value) => value
    }

}
