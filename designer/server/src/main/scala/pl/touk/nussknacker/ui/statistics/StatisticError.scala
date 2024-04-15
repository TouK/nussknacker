package pl.touk.nussknacker.ui.statistics

sealed trait StatisticError
case object DbError  extends StatisticError
case object UrlError extends StatisticError
