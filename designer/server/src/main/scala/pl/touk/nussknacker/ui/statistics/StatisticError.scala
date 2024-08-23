package pl.touk.nussknacker.ui.statistics

sealed trait StatisticError
object CannotGenerateStatisticsError extends StatisticError
object CannotEncryptURLError         extends StatisticError
