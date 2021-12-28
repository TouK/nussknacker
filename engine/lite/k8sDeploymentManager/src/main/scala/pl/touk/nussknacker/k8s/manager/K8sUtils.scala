package pl.touk.nussknacker.k8s.manager

import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.process.ProcessName
import skuber.LabelSelector
import skuber.LabelSelector.dsl._

import scala.language.reflectiveCalls

object K8sUtils {


  val scenarioNameLabel: String = "nussknacker.io/scenarioName"

  val scenarioIdLabel: String = "nussknacker.io/scenarioId"

  val scenarioVersionLabel: String = "nussknacker.io/scenarioVersion"

  //TODO: we need some hash to avoid name clashes :/ Or pass ProcessId in findJobStatus/cancel
  private[manager] def labelSelectorForName(processName: ProcessName) =
    LabelSelector(scenarioNameLabel is sanitizeNameLabel(processName))

  private[manager] def objectNameForScenario(processVersion: ProcessVersion): String = {
    sanitizeName(s"scenario-${processVersion.processId.value}-${processVersion.processName}", canHaveUnderscore = false)
  }

  private[manager] def sanitizeNameLabel(processName: ProcessName): String = {
    sanitizeName(processName.value, canHaveUnderscore = true)
  }

  //TODO: generate better correct name for 'strange' scenario names?
  //Value label: https://kubernetes.io/docs/concepts/overview/working-with-objects/labels/#syntax-and-character-set
  //Object names cannot have underscores in name...
  private[manager] def sanitizeName(base: String, canHaveUnderscore: Boolean): String = {
    val underscores = if (canHaveUnderscore) "_" else ""
    base.toLowerCase
      .replaceAll(s"[^a-zA-Z0-9${underscores}\\-.]+", "-")
      //need to have alphanumeric at beginning and end...
      .replaceAll("^([^a-zA-Z0-9])", "x$1")
      .replaceAll("([^a-zA-Z0-9])$", "$1x")
      .take(63)
  }

}
