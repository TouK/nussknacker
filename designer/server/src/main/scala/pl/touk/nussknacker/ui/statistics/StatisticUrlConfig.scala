package pl.touk.nussknacker.ui.statistics

final case class StatisticUrlConfig(
    // https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cloudfront-limits.html#limits-general
    // 300 bytes less for 256 key size + 33% increase from encryption
    urlBytesSizeLimit: Int = 6700,
    nuStatsUrl: String = "https://stats.nussknacker.io/?",
    queryParamsSeparator: String = "&",
    emptyString: String = "",
    // TODO: switch to true once logstash is ready
    encryptQueryParams: Boolean = false
)
