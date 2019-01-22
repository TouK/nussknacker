package pl.touk.nussknacker.ui.validation

import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.displayedgraph.displayablenode.ProcessAdditionalFields
import pl.touk.nussknacker.restmodel.validation.ValidationResults.{NodeValidationError, ValidationResult}
import pl.touk.nussknacker.ui.definition.{AdditionalProcessProperty, PropertyType}

import scala.util.Try

class AdditionalPropertiesValidator(additionalFieldsConfig: Map[ProcessingType, Map[String, AdditionalProcessProperty]],
                                    errorType: String) {
  import cats.data._
  import cats.implicits._
  import shapeless.syntax.typeable._

  private type Validation[T] = ValidatedNel[NodeValidationError, T]
  private type PropertiesConfig = Map[String, AdditionalProcessProperty]

  def validate(process: DisplayableProcess): ValidationResult = {
    additionalFieldsConfig.get(process.processingType) match {
      case None =>
        ValidationResult.errors(Map(), List(), List(PrettyValidationErrors.noValidatorKnown(process.processingType)))
      case Some(propertiesConfig) =>
        val additionalFields = process
          .metaData
          .additionalFields
          .flatMap(_.cast[ProcessAdditionalFields])

        val errors = (
          additionalFields.map(validateNonEmptiness(propertiesConfig, _)).getOrElse(().validNel),
          additionalFields.map(validateTypes(propertiesConfig, _)).getOrElse(().validNel)
        ) .mapN((_, _) => Unit)
          .fold(_.toList, _ => Nil)
        ValidationResult.errors(Map(), errors, List())

    }
  }

  private def validateNonEmptiness(propertiesConfig: PropertiesConfig, fields: ProcessAdditionalFields): Validation[Unit] = {
    val nonEmptyFields = fields
      .properties
      .filterNot { case (_, value) => value.isEmpty }
      .keys.toList
    val errors = propertiesConfig
      .filter(_._2.isRequired)
      .filterNot(field => nonEmptyFields.contains(field._1))
      .map(field => PrettyValidationErrors.emptyRequiredField(errorType, field._1, field._2.label))

    NonEmptyList.fromList(errors.toList)
      .fold(().validNel[NodeValidationError])(_.invalid)
  }

  private def validateTypes(propertiesConfig: PropertiesConfig, fields: ProcessAdditionalFields): Validation[Unit] = {
    fields.properties.map { case (name, value) =>
      propertiesConfig.get(name) match {
        case Some(config) =>
          validateValue(name, config, value, config.`type`)
        case None =>
          Validated.invalidNel(PrettyValidationErrors.unknownProperty(errorType, name))
      }

    } .toList
      .sequence
      .map(_ => Unit)
  }

  private def validateValue(name: String, property: AdditionalProcessProperty, value: String, typ: PropertyType.Value): Validation[Unit] = {
    val isValid = typ match {
      case PropertyType.select if property.values.getOrElse(Nil).contains(value) => true
      case PropertyType.select => false

      case PropertyType.integer if Try(value.toInt).isSuccess => true
      case PropertyType.integer => false

      case _ => true
    }

    Either.cond(isValid, (), typeError(name, property, value, typ)).toValidatedNel
  }

  private def typeError(name: String, property: AdditionalProcessProperty, fieldValue: String, fieldType: PropertyType.Value) = {
    PrettyValidationErrors.invalidFieldValueType(errorType, name, property, fieldType, fieldValue)
  }
}
