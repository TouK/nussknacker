package pl.touk.nussknacker.ui.api

import io.circe.generic.JsonCodec

import scala.util.Random
import pl.touk.nussknacker.engine.version.BuildInfo

import java.net.URLEncoder
import java.nio.charset.StandardCharsets

private object FingerprintUtils {
  lazy val random = s"gen-${Random.alphanumeric.take(10).mkString}"
}

case class UsageStatisticsReportsConfig(enabled: Boolean, fingerprint: String = FingerprintUtils.random)

object UsageStatisticsUrl {
  def apply(fingerprint: String, version: String) = s"https://stats.nussknacker.io/?fingerprint=${URLEncoder.encode(fingerprint, StandardCharsets.UTF_8)}&version=${URLEncoder.encode(version, StandardCharsets.UTF_8)}"
}

object UsageStatisticsReportsSettings {
  def apply(config: UsageStatisticsReportsConfig): UsageStatisticsReportsSettings = {
    UsageStatisticsReportsSettings(config.enabled, UsageStatisticsUrl(config.fingerprint, BuildInfo.version))
  }
}

@JsonCodec case class UsageStatisticsReportsSettings(enabled: Boolean, url: String)