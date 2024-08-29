package pl.touk.nussknacker.ui.statistics

final case class StatisticUrlConfig(
    // https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cloudfront-limits.html#limits-general
    // 400 bytes less: 256 bytes for secret key encrypted with rsa + 16 for padding in aes times 1.33 due to base64
    // 1700 less for increase in size due to base64
    // 7000 - 1700 - 400
    urlBytesSizeLimit: Int = 4900,
    nuStatsUrl: String = "https://stats.nussknacker.io/?",
    queryParamsSeparator: String = "&",
    emptyString: String = "",
    publicEncryptionKey: PublicEncryptionKey = PublicEncryptionKey.INSTANCE
)
