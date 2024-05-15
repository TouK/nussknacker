package pl.touk.nussknacker.ui.db.timeseries

import scala.language.higherKinds

trait FEStatisticsRepository[F[_]] extends ReadFEStatisticsRepository[F] with WriteFEStatisticsRepository[F]

trait ReadFEStatisticsRepository[F[_]] {
  def read(): F[Map[String, Long]]
}

trait WriteFEStatisticsRepository[F[_]] {
  def write(statistics: Map[String, Long]): F[Unit]
}
