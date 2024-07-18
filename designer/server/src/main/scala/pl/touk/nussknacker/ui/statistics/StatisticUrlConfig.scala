package pl.touk.nussknacker.ui.statistics

final case class StatisticUrlConfig(
    // https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cloudfront-limits.html#limits-general
    urlBytesSizeLimit: Int = 7000,
    nuStatsUrl: String = "https://stats.nussknacker.io/?",
    queryParamsSeparator: String = "&",
    emptyString: String = "",
    encryptQueryParams: Boolean = true
)
