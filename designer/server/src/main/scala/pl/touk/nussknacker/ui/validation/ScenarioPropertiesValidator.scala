package pl.touk.nussknacker.ui.validation

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid, invalid, valid}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingRequiredProperty, UnknownProperty}
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.component.ScenarioPropertyConfig
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.graph.ScenarioGraph
import pl.touk.nussknacker.engine.graph.expression.Expression
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.definition.scenarioproperty.ScenarioPropertyValidatorDeterminerChain
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.util.Try

class ScenarioPropertiesValidator(
    scenarioPropertiesConfig: Map[String, ScenarioPropertyConfig]
) {

  import cats.implicits._

  implicit val nodeId: NodeId = NodeId("properties")

  type PropertyConfig = Map[String, ScenarioPropertyConfig]

  def validate(scenarioProperties: List[(String, String)]): ValidationResult = {
    val validated = (
      getConfiguredValidationsResults(scenarioPropertiesConfig, scenarioProperties),
      getMissingRequiredPropertyValidationResults(scenarioPropertiesConfig, scenarioProperties),
      getUnknownPropertyValidationResults(scenarioPropertiesConfig, scenarioProperties)
    )
      .mapN { (_, _, _) => () }

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
      validator <- validatorsByPropertyName.getOrElse(property._1, List.empty)
    } yield (property, config.get(property._1), validator)

    propertiesWithConfiguredValidator
      .collect { case (property, Some(config), validator: ParameterValidator) =>
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
        validator.isValid(property._1, Expression.spel(expression), Some(value), config.label).toValidatedNel
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

  private def getUnknownPropertyValidationResults(
      config: PropertyConfig,
      scenarioProperties: List[(String, String)]
  ) = {
    scenarioProperties
      .map(property => (property._1, UnknownPropertyValidator(config)))
      .map { case (propertyName, validator) =>
        validator.isValid(propertyName).toValidatedNel
      }
      .sequence
      .map(_ => ())
  }

}

private final case class MissingRequiredPropertyValidator(actualPropertyNames: List[String]) {

  def isValid(propertyName: String, label: Option[String] = None)(
      implicit nodeId: NodeId
  ): Validated[PartSubGraphCompilationError, Unit] = {

    if (actualPropertyNames.contains(propertyName)) valid(()) else invalid(MissingRequiredProperty(propertyName, label))
  }

}

private final case class UnknownPropertyValidator(config: Map[String, ScenarioPropertyConfig]) {

  def isValid(propertyName: String)(implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {

    if (config.get(propertyName).nonEmpty) valid(()) else invalid(UnknownProperty(propertyName))
  }

}
