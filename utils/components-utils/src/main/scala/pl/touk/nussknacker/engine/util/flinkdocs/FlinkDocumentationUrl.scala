package pl.touk.nussknacker.engine.util.flinkdocs

import org.semver4j.Semver
import pl.touk.nussknacker.engine.version.BuildInfo

object FlinkDocumentationUrl {

  private val currentFlinkVersionShort = {
    val semver = Semver.parse(BuildInfo.flinkVersion)
    semver.getMajor + "." + semver.getMinor
  }

  def forCurrentFlinkVersion(path: String): String = {
    s"https://nightlies.apache.org/flink/flink-docs-release-$currentFlinkVersionShort/docs/$path"
  }

}
