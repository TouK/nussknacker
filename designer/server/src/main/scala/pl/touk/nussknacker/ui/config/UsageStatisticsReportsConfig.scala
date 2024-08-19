package pl.touk.nussknacker.ui.config

final case class UsageStatisticsReportsConfig(
    enabled: Boolean,
    errorReportsEnabled: Boolean = true,
    // unique identifier for Designer installation
    fingerprint: Option[String],
    // source from which Nussknacker was downloaded
    source: Option[String]
)
