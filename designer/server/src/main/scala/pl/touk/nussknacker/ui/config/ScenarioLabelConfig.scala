package pl.touk.nussknacker.ui.config

import cats.data.Validated.{Invalid, Valid}
import cats.data.ValidatedNel
import cats.implicits._
import com.typesafe.config.Config

import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.matching.Regex

final case class ScenarioLabelConfig private (validationRules: List[ScenarioLabelConfig.ValidationRule])

object ScenarioLabelConfig {

  final case class ValidationRule(validationRegex: Regex, message: String) {
    def messageWithLabel(label: String): String =
      message.replace("{label}", label)
  }

  def create(rules: List[ScenarioLabelConfig.ValidationRule]): ScenarioLabelConfig = new ScenarioLabelConfig(rules)

  private[config] def create(config: Config): Option[ScenarioLabelConfig] = {
    val rootPath = "scenarioLabelSettings"
    if (config.hasPath(rootPath)) {
      val settingConfig = config.getConfig(rootPath)
      createValidationRules(settingConfig) match {
        case Valid(rules) => Some(new ScenarioLabelConfig(rules))
        case Invalid(errors) =>
          throw new IllegalArgumentException(
            s"Invalid configuration for '$rootPath'. Details: ${errors.map(_.message).mkString_(",")}"
          )
      }
    } else {
      None
    }
  }

  private def createValidationRules(settingConfig: Config) = {
    settingConfig
      .getConfigList(s"validationRules")
      .asScala
      .map(createRule)
      .toList
      .sequence
  }

  private def createRule(config: Config): ValidatedNel[ScenarioLabelValidationRuleError, ValidationRule] = {
    val validationPattern = config.getString("validationPattern")

    Try(validationPattern.r).toValidated
      .leftMap((_: Throwable) => ScenarioLabelValidationRuleError(s"Incorrect validationPattern: $validationPattern"))
      .toValidatedNel
      .map { regex: Regex =>
        ValidationRule(
          validationRegex = regex,
          message = config.getString("validationMessage")
        )
      }
  }

  private final case class ScenarioLabelValidationRuleError(message: String)
}
