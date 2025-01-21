package pl.touk.nussknacker.ui.statistics

import pl.touk.nussknacker.ui.api.description.StatisticsApiEndpoints.Dtos.RegisterStatisticsRequestDto

final case class RawFEStatistics(raw: Map[String, Long])

object RawFEStatistics {
  val empty = new RawFEStatistics(Map.empty)

  def apply(request: RegisterStatisticsRequestDto): RawFEStatistics =
    new RawFEStatistics(
      // TODO: change to groupMapReduce in scala 2.13
      raw = request.statistics
        .groupBy(_.name.shortName)
        .map { case (k, v) =>
          k -> v.size.toLong
        }
    )

}
