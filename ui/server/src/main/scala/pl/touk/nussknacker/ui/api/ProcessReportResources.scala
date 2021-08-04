package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.Unmarshaller
import akka.stream.Materializer
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax._
import pl.touk.nussknacker.processCounts._
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.processreport.{ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.{Instant, OffsetDateTime, ZonedDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ProcessReportResources(countsReporter: CountsReporter, processCounter: ProcessCounter, val processRepository: FetchingProcessRepository[Future])
                            (implicit val ec: ExecutionContext) extends Directives with FailFastCirceSupport with RouteWithUser with ProcessDirectives {

  private implicit val offsetDateTimeToInstant: Unmarshaller[String, Instant] = new Unmarshaller[String, Instant] {
    override def apply(value: String)(implicit ec: ExecutionContext, materializer: Materializer): Future[Instant] = {
      Future.fromTry(Try(OffsetDateTime.parse(value).toInstant))
    }
  }

  def securedRoute(implicit loggedUser: LoggedUser): Route = {
    path("processCounts" / Segment) { processName =>
      (get & processId(processName) & parameters('dateFrom.as[Option[Instant]], 'dateTo.as[Option[Instant]])) { (processId, dateFrom, dateTo) =>
        val request = prepareRequest(dateFrom, dateTo)
        complete {
          processRepository.fetchLatestProcessDetailsForProcessId[DisplayableProcess](processId.id).flatMap[ToResponseMarshallable] {
            case Some(process) =>
              process.json match {
                case Some(displayable) => computeCounts(displayable, request)
                case None => Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Counts unavailable for this scenario"))
              }
            case None => Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Scenario not found"))
          }
        }
      }
    }
  }

  private def prepareRequest(dateFromO: Option[Instant], dateToO: Option[Instant]): CountsRequest = {
    val dateTo = dateToO
      .filterNot(_.isAfter(Instant.now()))
      .getOrElse(Instant.now())
    dateFromO match {
      case Some(dateFrom) =>
        RangeCount(dateFrom, dateTo)
      case None =>
        ExecutionCount(dateTo)
    }
  }

  private def computeCounts(process: DisplayableProcess, countsRequest: CountsRequest): Future[ToResponseMarshallable] = {
    countsReporter.prepareRawCounts(process.id, countsRequest)
      .map(computeFinalCounts(process, _))
      .recover {
        case CannotFetchCountsError(msg) => HttpResponse(status = StatusCodes.BadRequest, entity = msg)
      }
  }


  private def computeFinalCounts(displayable: DisplayableProcess, nodeCountFunction: String => Option[Long]): ToResponseMarshallable = {
    val computedCounts = processCounter.computeCounts(ProcessConverter.fromDisplayable(displayable),
      nodeId => nodeCountFunction(nodeId).map(count => RawCount(count, 0)))
    computedCounts.asJson
  }

}