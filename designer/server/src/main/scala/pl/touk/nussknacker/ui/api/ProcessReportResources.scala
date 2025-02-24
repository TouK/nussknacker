package pl.touk.nussknacker.ui.api

import org.apache.pekko.http.scaladsl.marshalling.ToResponseMarshallable
import org.apache.pekko.http.scaladsl.model._
import org.apache.pekko.http.scaladsl.server._
import org.apache.pekko.http.scaladsl.unmarshalling.Unmarshaller
import org.apache.pekko.http.scaladsl.util.FastFuture
import org.apache.pekko.stream.Materializer
import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import io.circe.syntax._
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.processCounts._
import pl.touk.nussknacker.ui.process.ProcessService
import pl.touk.nussknacker.ui.process.marshall.CanonicalProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.processreport.{ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import java.time.{Instant, OffsetDateTime}
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try

class ProcessReportResources(
    countsReporter: CountsReporter[Future],
    processCounter: ProcessCounter,
    processRepository: FetchingProcessRepository[Future],
    protected val processService: ProcessService
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with ProcessDirectives {

  private implicit val offsetDateTimeToInstant: Unmarshaller[String, Instant] = new Unmarshaller[String, Instant] {

    override def apply(value: String)(implicit ec: ExecutionContext, materializer: Materializer): Future[Instant] = {
      FastFuture(Try(OffsetDateTime.parse(value).toInstant))
    }

  }

  def securedRoute(implicit loggedUser: LoggedUser): Route = {
    path("processCounts" / ProcessNameSegment) { processName =>
      (get & processId(processName) & parameters(
        Symbol("dateFrom").as[Instant].optional,
        Symbol("dateTo").as[Instant].optional
      )) { (processId, dateFrom, dateTo) =>
        val request = prepareRequest(dateFrom, dateTo)
        complete {
          processRepository
            .fetchLatestProcessDetailsForProcessId[ScenarioGraph](processId.id)
            .flatMap[ToResponseMarshallable] {
              case Some(process) => computeCounts(processName, process.json, process.isFragment, request)
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

  private def computeCounts(
      processName: ProcessName,
      scenarioGraph: ScenarioGraph,
      isFragment: Boolean,
      countsRequest: CountsRequest
  )(implicit loggedUser: LoggedUser): Future[ToResponseMarshallable] = {
    countsReporter
      .prepareRawCounts(processName, countsRequest)
      .map(computeFinalCounts(processName, scenarioGraph, isFragment, _))
      .recover { case CannotFetchCountsError(msg) =>
        HttpResponse(status = StatusCodes.BadRequest, entity = msg)
      }
  }

  private def computeFinalCounts(
      processName: ProcessName,
      scenarioGraph: ScenarioGraph,
      isFragment: Boolean,
      nodeCountFunction: String => Option[Long]
  )(implicit loggedUser: LoggedUser): ToResponseMarshallable = {
    val computedCounts = processCounter.computeCounts(
      CanonicalProcessConverter.fromScenarioGraph(scenarioGraph, processName),
      isFragment,
      nodeId => nodeCountFunction(nodeId).map(count => RawCount(count, 0))
    )
    computedCounts.asJson
  }

}
