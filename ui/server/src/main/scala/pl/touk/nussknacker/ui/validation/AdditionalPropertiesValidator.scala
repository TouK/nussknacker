package pl.touk.nussknacker.ui.validation

import cats.data.Validated
import cats.data.Validated.{Invalid, Valid, invalid, valid}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingRequiredProperty, NodeId, UnknownProperty}
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.definition.additionalproperty.AdditionalPropertyValidatorDeterminerChain
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider


class AdditionalPropertiesValidator(additionalPropertiesConfig: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig]]) {

  import cats.implicits._

  implicit val nodeId: NodeId = NodeId("properties")

  type PropertyConfig = Map[String, AdditionalPropertyConfig]

  def validate(process: DisplayableProcess): ValidationResult = additionalPropertiesConfig.forType(process.processingType) match {
    case None =>
      ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.noValidatorKnown(process.processingType)))

    case Some(config) => {
      val additionalProperties = process.metaData.additionalFields
        .map(field => field.properties)
        .getOrElse(Map.empty)
        .toList

      val validated = (
        getConfiguredValidationsResults(config, additionalProperties),
        getMissingRequiredPropertyValidationResults(config, additionalProperties),
        getUnknownPropertyValidationResults(config, additionalProperties)
        )
        .mapN { (_, _, _) => Unit }

      val processPropertiesErrors = validated match {
        case Invalid(e) => e.map(error => PrettyValidationErrors.formatErrorMessage(error)).toList
        case Valid(_) => List.empty
      }

      ValidationResult.errors(Map(), processPropertiesErrors, List())
    }
  }

  private def getConfiguredValidationsResults(config: PropertyConfig, additionalProperties: List[(String, String)]) = {
    val validatorsByPropertyName = config
      .map(propertyConfig => propertyConfig._1 -> AdditionalPropertyValidatorDeterminerChain(propertyConfig._2).determine())

    val propertiesWithConfiguredValidator = for {
      property <- additionalProperties
      validator <- validatorsByPropertyName.getOrElse(property._1, List.empty)
    } yield (property, config.get(property._1), validator)

    propertiesWithConfiguredValidator.collect {
      case (property, Some(config), validator: ParameterValidator) =>
        validator.isValid(property._1, property._2, config.label).toValidatedNel
    }.sequence.map(_ => Unit)
  }

  private def getMissingRequiredPropertyValidationResults(config: PropertyConfig, additionalProperties: List[(String, String)]) = {
    config
      .filter(_._2.validators.nonEmpty)
      .filter(_._2.validators.get.contains(MandatoryParameterValidator))
      .map(propertyConfig => (propertyConfig._1, propertyConfig._2, MissingRequiredPropertyValidator(additionalProperties.map(_._1))))
      .toList.map {
      case (propertyName, config, validator) => validator.isValid(propertyName, config.label).toValidatedNel
    }
      .sequence.map(_ => Unit)
  }

  private def getUnknownPropertyValidationResults(config: PropertyConfig, additionalProperties: List[(String, String)]) = {
    additionalProperties
      .map(property => (property._1, UnknownPropertyValidator(config)))
      .map {
        case (propertyName, validator) => validator.isValid(propertyName).toValidatedNel
      }
      .sequence.map(_ => Unit)
  }
}

private case class MissingRequiredPropertyValidator(actualPropertyNames: List[String]) {

  def isValid(propertyName: String, label: Option[String] = None)
             (implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {

    if (actualPropertyNames.contains(propertyName)) valid(Unit) else invalid(MissingRequiredProperty(propertyName, label))
  }
}

private case class UnknownPropertyValidator(config: Map[String, AdditionalPropertyConfig]) {

  def isValid(propertyName: String)
             (implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {

    if (config.get(propertyName).nonEmpty) valid(Unit) else invalid(UnknownProperty(propertyName))
  }
}

