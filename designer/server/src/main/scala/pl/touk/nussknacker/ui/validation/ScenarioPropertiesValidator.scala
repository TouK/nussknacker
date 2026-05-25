package pl.touk.nussknacker.ui.validation

import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{invalid, valid, Invalid, Valid}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{CannotCreateObjectError, MissingRequiredProperty}
import pl.touk.nussknacker.engine.api.definition.{CompileTimeValidator, MandatoryParameterValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.definition.ScenarioPropertiesConfigFinalizer
import pl.touk.nussknacker.ui.definition.scenarioproperty.ScenarioPropertyValidatorDeterminerChain

import scala.util.Try

class ScenarioPropertiesValidator(
    scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig],
    scenarioPropertiesConfigFinalizer: ScenarioPropertiesConfigFinalizer
) {

  import cats.implicits._

  implicit val nodeId: NodeId = NodeId("properties")

  type PropertyConfig = Map[String, ScenarioPropertyConfig]

  def validate(scenarioProperties: List[(String, String)]): ValidationResult = {
    val finalizedScenarioPropertiesConfig =
      scenarioPropertiesConfigFinalizer.finalizeScenarioProperties(scenarioPropertiesConfig)

    val validated = (
      getConfiguredValidationsResults(finalizedScenarioPropertiesConfig, scenarioProperties),
      getMissingRequiredPropertyValidationResults(finalizedScenarioPropertiesConfig, scenarioProperties),
    )
      .mapN { (_, _) => () }

    val processPropertiesErrors = validated match {
      case Invalid(e) => e.map(error => PrettyValidationErrors.formatErrorMessage(error)).toList
      case Valid(_)   => List.empty
    }

    ValidationResult.errors(Map(), processPropertiesErrors, List())
  }

  private def getConfiguredValidationsResults(config: PropertyConfig, scenarioProperties: List[(String, String)]) = {
    val validatorsByPropertyName = config
      .map(propertyConfig =>
        propertyConfig._1 -> ScenarioPropertyValidatorDeterminerChain(propertyConfig._2).determine()
      )

    val propertiesWithConfiguredValidator = for {
      property  <- scenarioProperties
      validator <- ParameterValidator.resolveLoaders(validatorsByPropertyName.getOrElse(property._1, List.empty))
    } yield (property, config.get(property._1), validator)

    propertiesWithConfiguredValidator
      .collect { case (property, Some(config), validator: CompileTimeValidator) =>
        val expression = property._2
        val value = expression match {
          case ex if ex.isBlank => null
          // FIXME: scenario properties does not have TypingResult hence this little hack to convert them to proper values
          case _ =>
            Try[Any] { expression.toBoolean }
              .orElse(Try { expression.toInt })
              .orElse(Try { expression.toDouble })
              .getOrElse(expression)
        }
        // FIXME: is this really Spel or just Literal expression? (currently we only have spel and spel template)
        validator
          .isValid(ParameterName(property._1), Expression.spel(expression), Some(value), config.label)
          .toValidatedNel
      }
      .sequence
      .map(_ => ())
  }

  private def getMissingRequiredPropertyValidationResults(
      config: PropertyConfig,
      scenarioProperties: List[(String, String)]
  ) = {
    config
      .filter(_._2.validators.nonEmpty)
      .filter(_._2.validators.get.contains(MandatoryParameterValidator))
      .map(propertyConfig =>
        (propertyConfig._1, propertyConfig._2, MissingRequiredPropertyValidator(scenarioProperties.map(_._1)))
      )
      .toList
      .map { case (propertyName, config, validator) =>
        validator.isValid(propertyName, config.label).toValidatedNel
      }
      .sequence
      .map(_ => ())
  }

}

private final case class MissingRequiredPropertyValidator(actualPropertyNames: List[String]) {

  def isValid(propertyName: String, label: Option[String] = None)(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {
    if (actualPropertyNames.contains(propertyName)) {
      valid(())
    } else {
      invalid(MissingRequiredProperty(ParameterName(propertyName), label))
    }
  }

}
