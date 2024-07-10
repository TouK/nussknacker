package pl.touk.nussknacker.ui.db.timeseries

import pl.touk.nussknacker.ui.statistics.RawFEStatistics

import scala.concurrent.Future

object NoOpFEStatisticsRepository extends FEStatisticsRepository[Future] {

  override def read(): Future[RawFEStatistics] = Future.successful(RawFEStatistics.empty)

  override def write(statistics: RawFEStatistics): Future[Unit] = Future.successful(())
}
