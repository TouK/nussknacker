package pl.touk.process.report

import java.time.LocalDateTime

import scala.concurrent.{ExecutionContext, Future}

//In the future we can extract it to enable metric backends other than influx
trait CountsReporter {

  def prepareRawCounts(processId: String, fromDate: LocalDateTime, toDate: LocalDateTime)(implicit ec: ExecutionContext): Future[(String => Option[Long])]

}

case class CannotFetchCountsError(msg: String) extends Exception(msg)


