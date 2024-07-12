package pl.touk.nussknacker.ui.db.timeseries

import pl.touk.nussknacker.ui.statistics.RawFEStatistics

import scala.language.higherKinds

trait FEStatisticsRepository[F[_]] extends ReadFEStatisticsRepository[F] with WriteFEStatisticsRepository[F]

trait ReadFEStatisticsRepository[F[_]] {
  def read(): F[RawFEStatistics]
}

trait WriteFEStatisticsRepository[F[_]] {
  def write(statistics: RawFEStatistics): F[Unit]
}
