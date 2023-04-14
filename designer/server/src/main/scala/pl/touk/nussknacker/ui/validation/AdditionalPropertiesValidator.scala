package pl.touk.nussknacker.ui.validation

import cats.data.{NonEmptyMap, Validated}
import cats.data.Validated.{Invalid, Valid, invalid, valid}
import pl.touk.nussknacker.engine.api.context.PartSubGraphCompilationError
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.{MissingRequiredProperty, UnknownProperty}
import pl.touk.nussknacker.engine.api.definition.{MandatoryParameterValidator, ParameterValidator}
import pl.touk.nussknacker.engine.api.component.AdditionalPropertyConfig
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.definition.additionalproperty.AdditionalPropertyValidatorDeterminerChain
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider

class AdditionalPropertiesValidator(additionalPropertiesConfig: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig], _],
                                    typeSpecificPropertiesConfig: ProcessingTypeDataProvider[Map[String, AdditionalPropertyConfig], _]) {

  import cats.implicits._

  implicit val nodeId: NodeId = NodeId("properties")

  type PropertyConfig = Map[String, AdditionalPropertyConfig]

  private def getPropertiesConfig(process: DisplayableProcess): Option[Map[String, AdditionalPropertyConfig]] = {
    val additonal = additionalPropertiesConfig.forType(process.processingType)
    val typeSpecific = typeSpecificPropertiesConfig.forType(process.processingType)

    if (additonal.isEmpty && typeSpecific.isEmpty) None
    else Some(additonal.getOrElse(Map()) ++ typeSpecific.getOrElse(Map()))
  }

  def validate(process: DisplayableProcess): ValidationResult = getPropertiesConfig(process) match {
    case None =>
      ValidationResult.globalErrors(List(PrettyValidationErrors.noValidatorKnown(process.processingType)))

    case Some(config) => {

      val additionalProperties = process.properties.additionalFields
        .map(field => field.properties)
        .getOrElse(Map.empty)
        .toList

      val typeSpecProps = process.properties.typeSpecificProperties.properties.toList

      val props = List(typeSpecProps, additionalProperties).flatten

      val validated = (
        getConfiguredValidationsResults(config, props),
        getMissingRequiredPropertyValidationResults(config, props),
        getUnknownPropertyValidationResults(config, props)
        )
        .mapN { (_, _, _) => () }

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
    }.sequence.map(_ => ())
  }

  private def getMissingRequiredPropertyValidationResults(config: PropertyConfig, additionalProperties: List[(String, String)]) = {
    config
      .filter(_._2.validators.nonEmpty)
      .filter(_._2.validators.get.contains(MandatoryParameterValidator))
      .map(propertyConfig => (propertyConfig._1, propertyConfig._2, MissingRequiredPropertyValidator(additionalProperties.map(_._1))))
      .toList.map {
      case (propertyName, config, validator) => validator.isValid(propertyName, config.label).toValidatedNel
    }
      .sequence.map(_ => ())
  }

  private def getUnknownPropertyValidationResults(config: PropertyConfig, additionalProperties: List[(String, String)]) = {
    additionalProperties
      .map(property => (property._1, UnknownPropertyValidator(config)))
      .map {
        case (propertyName, validator) => validator.isValid(propertyName).toValidatedNel
      }
      .sequence.map(_ => ())
  }
}

private case class MissingRequiredPropertyValidator(actualPropertyNames: List[String]) {

  def isValid(propertyName: String, label: Option[String] = None)
             (implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {

    if (actualPropertyNames.contains(propertyName)) valid(()) else invalid(MissingRequiredProperty(propertyName, label))
  }
}

private case class UnknownPropertyValidator(config: Map[String, AdditionalPropertyConfig]) {

  def isValid(propertyName: String)
             (implicit nodeId: NodeId): Validated[PartSubGraphCompilationError, Unit] = {

    if (config.get(propertyName).nonEmpty) valid(()) else invalid(UnknownProperty(propertyName))
  }
}

