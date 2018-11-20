package pl.touk.nussknacker.processCounts

import java.time.LocalDateTime

import scala.concurrent.{ExecutionContext, Future}

//In the future we can extract it to enable metric backends other than influx
trait CountsReporter {

  def prepareRawCounts(processId: String, countsRequest: CountsRequest)(implicit ec: ExecutionContext): Future[String => Option[Long]]

}

case class CannotFetchCountsError(msg: String) extends Exception(msg)

sealed trait CountsRequest

case class RangeCount(fromDate: LocalDateTime, toDate: LocalDateTime) extends CountsRequest

case class ExecutionCount(pointInTime: LocalDateTime) extends CountsRequest

