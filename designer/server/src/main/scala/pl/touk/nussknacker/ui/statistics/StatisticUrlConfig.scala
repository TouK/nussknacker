package pl.touk.nussknacker.ui.statistics

final case class StatisticUrlConfig(
    // https://docs.aws.amazon.com/AmazonCloudFront/latest/DeveloperGuide/cloudfront-limits.html#limits-general
    // 400 bytes less: 256 bytes for secret key encrypted with rsa + 16 for padding in aes times 1.33 due to base64
    // 1640 less for increase in size due to base64
    // 7000 - 1640 - 400
    urlBytesSizeLimit: Int = 4960,
    nuStatsUrl: String = "https://stats.nussknacker.io/?",
    queryParamsSeparator: String = "&",
    emptyString: String = "",
    publicEncryptionKey: PublicEncryptionKey = PublicEncryptionKey(
      Some(
        "MIIBIjANBgkqhkiG9w0BAQEFAAOCAQ8AMIIBCgKCAQEAjdvfEFH8jBF56UZmtQv" +
          "UH1gUCnRkHTke/jnOEIjdSmSqoyWGJ9UKC/PgLYyqLqNRKq2eEmLr26tQRaIoOHLcdGlGZXIrRkWHAPH8QTGrDt4Qm/COB8BPS7oV2tATsUN7z" +
          "JNWfVRNDzcunzDwAtZKs4SDTsFLAPrZ5CKMt5JK9Q7Xrzekl5PunzJEuIOmlWusFanIqCgs0d245NVRKhbkqq/JkoseEB4sDXFwNyO7sO51aLg" +
          "DST+P/G+tPveQpMusbGBK48Syce93R6bTf0Bd8KGaJQWvJPjD6rbX+K3vSQHqgb+NjIyUAWWNHuFvRt2NQ5iHsuaBU0B/61W8y6oMxQIDAQAB"
      )
    )
)
