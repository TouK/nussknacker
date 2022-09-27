package pl.touk.nussknacker.ui.validation

import cats.data.Validated.{Valid, invalid, valid}
import cats.data.{NonEmptyList, Validated, ValidatedNel}
import pl.touk.nussknacker.engine.CustomProcessValidator
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingRequiredProperty, PropertyValidationError, UnknownProperty}
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, ParameterValidator}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.ui.definition.additionalproperty.AdditionalPropertyValidatorDeterminerChain

class AdditionalPropertiesValidator(config: Map[String, AdditionalPropertyConfig]) extends CustomProcessValidator {

  import cats.implicits._

  //it's not really used, as we wrap errors in PropertyValidationError
  implicit val nodeId: NodeId = NodeId("properties")

  type ValidationResult = ValidatedNel[ProcessCompilationError, Unit]

  override def validate(process: CanonicalProcess): ValidationResult = {
    if (process.metaData.isSubprocess) {
      return Valid(())
    }

    val additionalProperties = process.metaData.additionalFields
      .map(field => field.properties)
      .getOrElse(Map.empty)
      .toList

    (getConfiguredValidationsResults(additionalProperties),
     getMissingRequiredPropertyValidationResults(additionalProperties),
     getUnknownPropertyValidationResults(additionalProperties)
     ).mapN { (_, _, _) => Unit }
  }

  private def getConfiguredValidationsResults(additionalProperties: List[(String, String)]) = {
    val validatorsByPropertyName = config
      .map(propertyConfig => propertyConfig._1 -> AdditionalPropertyValidatorDeterminerChain(propertyConfig._2).determine())

    val propertiesWithConfiguredValidator = for {
      property <- additionalProperties
      validator <- validatorsByPropertyName.getOrElse(property._1, List.empty)
    } yield (property, config.get(property._1), validator)

    propertiesWithConfiguredValidator.collect {
      case (property, Some(config), validator: ParameterValidator) =>
        validator.isValid(property._1, property._2, config.label).toValidatedNel.leftMap[NonEmptyList[ProcessCompilationError]](_.map(PropertyValidationError))
    }.sequence.map(_ => Unit)
  }

  private def getMissingRequiredPropertyValidationResults(additionalProperties: List[(String, String)]) = {
    config
      .filter(_._2.validators.nonEmpty)
      .filter(_._2.validators.get.contains(MandatoryParameterValidator))
      .map(propertyConfig => (propertyConfig._1, propertyConfig._2, MissingRequiredPropertyValidator(additionalProperties.map(_._1))))
      .toList.map {
      case (propertyName, config, validator) => validator.isValid(propertyName, config.label).toValidatedNel
    }
      .sequence.map(_ => Unit)
  }

  private def getUnknownPropertyValidationResults(additionalProperties: List[(String, String)]) = {
    additionalProperties
      .map(property => (property._1, UnknownPropertyValidator(config)))
      .map {
        case (propertyName, validator) => validator.isValid(propertyName).toValidatedNel
      }
      .sequence.map(_ => Unit)
  }
}

private case class MissingRequiredPropertyValidator(actualPropertyNames: List[String]) {
  def isValid(propertyName: String, label: Option[String] = None): Validated[ProcessCompilationError, Unit] = {
    if (actualPropertyNames.contains(propertyName)) valid(Unit) else invalid(MissingRequiredProperty(propertyName, label))
  }
}

private case class UnknownPropertyValidator(config: Map[String, AdditionalPropertyConfig]) {
  def isValid(propertyName: String): Validated[ProcessCompilationError, Unit] = {
    if (config.contains(propertyName)) valid(Unit) else invalid(UnknownProperty(propertyName))
  }
}

