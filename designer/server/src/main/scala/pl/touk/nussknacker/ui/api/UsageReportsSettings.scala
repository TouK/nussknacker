package pl.touk.nussknacker.ui.api

import io.circe.generic.JsonCodec

import scala.util.Random
import pl.touk.nussknacker.engine.version.BuildInfo

import java.net.URLEncoder
import java.util.Base64

private object FingerprintUtils {
  lazy val random = Random.alphanumeric.take(10).mkString
}

case class UsageReportsConfig(enabled: Boolean, fingerprint: String = FingerprintUtils.random)

object UsageReportsSettings {
  def apply(config: UsageReportsConfig): UsageReportsSettings = {
    val version = Base64.getEncoder.encodeToString(BuildInfo.version.getBytes())
    UsageReportsSettings(config.enabled, s"https://reports.nussknacker.io/?fingerprint=${config.fingerprint}&version=${URLEncoder.encode(version, "UTF-8")}")
  }
}

@JsonCodec case class UsageReportsSettings(enabled: Boolean, url: String)