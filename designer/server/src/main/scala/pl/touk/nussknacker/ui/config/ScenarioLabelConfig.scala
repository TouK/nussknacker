package pl.touk.nussknacker.ui.config

import cats.implicits._
import com.typesafe.config.Config

import scala.jdk.CollectionConverters._
import scala.util.Try
import scala.util.matching.Regex

final case class ScenarioLabelConfig private (validationRules: List[ScenarioLabelConfig.ValidationRule])

object ScenarioLabelConfig {

  final case class ValidationRule(validationRegex: Regex, message: String) {
    def messageWithLabel(label: String): String =
      message.replaceAll(s"""\\{label\\}""", label)
  }

  private[config] def create(config: Config): Option[ScenarioLabelConfig] = {
    val rootPath = "scenarioLabelSettings"
    if (config.hasPath(rootPath)) {
      val settingConfig = config.getConfig(rootPath)
      createValidationRules(settingConfig) match {
        case Right(rules) => Some(new ScenarioLabelConfig(rules))
        case Left(error) =>
          throw new IllegalArgumentException(
            s"Invalid configuration for '$rootPath'",
            error
          )
      }
    } else {
      None
    }
  }

  private def createValidationRules(settingConfig: Config) = {
    settingConfig
      .getConfigList("validationRules")
      .asScala
      .toList
      .traverse(createRule)
  }

  private def createRule(config: Config): Either[ScenarioLabelValidationRuleError, ValidationRule] = {
    val validationPattern = config.getString("validationPattern")

    Try(validationPattern.r).toEither
      .leftMap((ex: Throwable) =>
        ScenarioLabelValidationRuleError(s"Incorrect validationPattern: $validationPattern", ex)
      )
      .map { regex: Regex =>
        ValidationRule(
          validationRegex = regex,
          message = config.getString("validationMessage")
        )
      }
  }

  private final case class ScenarioLabelValidationRuleError(message: String, cause: Throwable)
      extends Exception(message, cause)
}
