package pl.touk.nussknacker.ui.db.timeseries

import scala.concurrent.ExecutionContext
import scala.language.higherKinds

trait FEStatisticsRepository[F[_]] {
  def write(statistics: Map[String, Long])(implicit ec: ExecutionContext): F[Unit]
  def read()(implicit ec: ExecutionContext): F[Map[String, Long]]
}
