package pl.touk.nussknacker.ui.statistics

final case class StatisticUrlConfig(
    // https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cloudfront-limits.html#limits-general
    // 300 bytes less: 256 bytes for secret key encrypted with rsa + 16 for padding in aes
    urlBytesSizeLimit: Int = 6700,
    nuStatsUrl: String = "https://stats.nussknacker.io/?",
    queryParamsSeparator: String = "&",
    emptyString: String = "",
    // TODO: switch to true once logstash is ready
    encryptQueryParams: Boolean = false
)
