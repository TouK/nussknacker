package pl.touk.nussknacker.engine.aws.managedflink

import pl.touk.nussknacker.engine.api.process.ProcessName

import scala.util.matching.Regex

final case class FlinkApplicationName(value: String) extends AnyVal

object FlinkApplicationName {

  val validationPattern: Regex = "^[a-zA-Z0-9-]+(?: +[a-zA-Z0-9-]+)*$".r

  implicit class ScenarioNameOps(val scenarioName: ProcessName) {

    def toFlinkApplicationName: FlinkApplicationName = {
      val value = scenarioName.value
      require(
        validationPattern.pattern.matcher(value).matches(),
        s"Invalid scenario name '$value'. Acceptable characters are digits, letters, hyphen (-) and space in the middle."
      )
      FlinkApplicationName(value.replace(' ', '_'))
    }

  }

  implicit class FlinkApplicationNameOps(val flinkAppName: FlinkApplicationName) {

    def toProcessName: ProcessName =
      ProcessName(flinkAppName.value.replace('_', ' '))

  }

}
