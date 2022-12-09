package pl.touk.nussknacker.ui.config

case class UsageStatisticsReportsConfig(enabled: Boolean,
                                        // unique identifier for Designer installation
                                        fingerprint: Option[String],
                                        // source from which Nussknacker was downloaded
                                        source: Option[String])
