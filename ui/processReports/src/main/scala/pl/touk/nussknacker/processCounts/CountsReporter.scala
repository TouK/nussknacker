package pl.touk.nussknacker.processCounts

import com.typesafe.config.Config
import sttp.client.{NothingT, SttpBackend}

import java.time.format.DateTimeFormatter
import java.time.{Instant, ZoneId}
import scala.concurrent.Future
import scala.language.higherKinds

object CountsReporterCreator {

  val reporterCreatorConfigPath = "countsSettings"

}

//By default it's InfluxCountsReporterCreator, other implementation can be used via ServiceLoaderMechanism
//@see NussknackerApp#prepareCountsReporter
trait CountsReporterCreator {

  def createReporter(env: String, config: Config)
                    (implicit backend: SttpBackend[Future, Nothing, NothingT]): CountsReporter[Future]

}


trait CountsReporter[F[_]] extends AutoCloseable {

  def prepareRawCounts(processId: String, countsRequest: CountsRequest): F[String => Option[Long]]

}

object CannotFetchCountsError {

  private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")

  def restartsDetected(dates: List[Instant]): CannotFetchCountsError = CannotFetchCountsError(
    s"Counts unavailable, as scenario was restarted/deployed on following dates: ${dates.map(_.atZone(ZoneId.systemDefault()).format(dateTimeFormatter)).mkString(", ")}"
  )
}

case class CannotFetchCountsError(msg: String) extends Exception(msg)

sealed trait CountsRequest

case class RangeCount(fromDate: Instant, toDate: Instant) extends CountsRequest

case class ExecutionCount(pointInTime: Instant) extends CountsRequest

