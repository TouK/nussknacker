package pl.touk.nussknacker.ui.db.timeseries

import scala.concurrent.Future

object NoOpFEStatisticsRepository extends FEStatisticsRepository[Future] {

  override def read(): Future[Map[String, Long]] = Future.successful(Map.empty)

  override def write(statistics: Map[String, Long]): Future[Unit] = Future.successful(())
}
