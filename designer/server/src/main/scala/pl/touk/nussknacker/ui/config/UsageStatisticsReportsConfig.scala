package pl.touk.nussknacker.ui.config

import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import scala.util.Random

private object FingerprintUtils {
  lazy val random = s"gen-${Random.alphanumeric.take(10).mkString}"
}

case class UsageStatisticsReportsConfig(enabled: Boolean, fingerprint: String = FingerprintUtils.random)

object UsageStatisticsUrl {
  def apply(fingerprint: String, version: String) =
    s"https://stats.nussknacker.io/?fingerprint=${URLEncoder.encode(fingerprint, StandardCharsets.UTF_8)}&version=${URLEncoder.encode(version, StandardCharsets.UTF_8)}&timestamp=${System.currentTimeMillis()}"
}