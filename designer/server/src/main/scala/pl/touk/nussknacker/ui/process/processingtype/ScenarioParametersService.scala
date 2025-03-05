package pl.touk.nussknacker.ui.process.processingtype

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.util.Implicits.{RichScalaMap, RichTupleList}
import pl.touk.nussknacker.restmodel.scenariodetails.ScenarioParameters
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.{BadRequestError, NuDesignerError, UnauthorizedError}
import pl.touk.nussknacker.ui.security.api.LoggedUser

class ScenarioParametersService private (
    parametersForProcessingType: Map[ProcessingType, ScenarioParameters],
    engineSetupErrors: Map[EngineSetupName, ValueWithRestriction[List[String]]]
) {

  def scenarioParametersCombinationsWithWritePermission(implicit loggedUser: LoggedUser): List[ScenarioParameters] =
    parametersForProcessingType.values.toList
      .filter { parameters =>
        loggedUser.can(parameters.category, Permission.Write)
      }

  def engineSetupErrorsWithWritePermission(implicit loggedUser: LoggedUser): Map[EngineSetupName, List[String]] =
    engineSetupErrors
      .flatMap { case (engineSetupName, errorsWithRestriction) =>
        errorsWithRestriction.valueWithAllowedAccess(Permission.Write).map(engineSetupName -> _)
      }

  // We decided to not show all parameters except processing mode when they are not needed to pick
  // the correct processing type. Because of that, all parameters are optional
  def queryProcessingTypeWithWritePermission(
      category: Option[String],
      processingMode: Option[ProcessingMode],
      engineSetupName: Option[EngineSetupName],
  )(implicit loggedUser: LoggedUser): Validated[NuDesignerError, ProcessingType] = {
    if (!category.forall(loggedUser.can(_, Permission.Write))) {
      invalid(new UnauthorizedError("User doesn't have access to the given category"))
    } else {
      val matchingProcessingTypes = parametersForProcessingType.toList.collect {
        case (processingType, parameters)
            if loggedUser.can(parameters.category, Permission.Write) &&
              processingMode.forall(_ == parameters.processingMode) &&
              engineSetupName.forall(_ == parameters.engineSetupName) &&
              category.forall(_ == parameters.category) =>
          processingType
      }
      matchingProcessingTypes match {
        case singleProcessingType :: Nil => valid(singleProcessingType)
        case Nil => invalid(new ProcessingTypeNotFoundError(category, processingMode, engineSetupName))
        case moreThanOne =>
          invalid(new MoreThanOneProcessingTypeFoundError(category, processingMode, engineSetupName, moreThanOne))
      }
    }
  }

  // TODO: This method is for legacy API purpose, it will be removed in 1.15
  def categoryUnsafe(processingType: ProcessingType): String =
    parametersForProcessingType
      .get(processingType)
      .map(parametersWithEngineErrors => parametersWithEngineErrors.category)
      .getOrElse(throw new IllegalStateException(s"Processing type: $processingType not found"))

  def getParametersWithReadPermissionUnsafe(
      processingType: ProcessingType
  )(implicit loggedUser: LoggedUser): ScenarioParameters = {
    val parameters = parametersForProcessingType(processingType)
    if (!loggedUser.can(parameters.category, Permission.Read)) {
      throw new UnauthorizedError("User doesn't have access to the given category")
    } else {
      parameters
    }
  }

}

object ScenarioParametersService {

  def createUnsafe(
      parametersWithEngineErrorsForProcessingType: Map[ProcessingType, ScenarioParametersWithEngineSetupErrors]
  ): ScenarioParametersService =
    create(parametersWithEngineErrorsForProcessingType).valueOr(ex => throw ex)

  def create(
      parametersWithEngineErrorsForProcessingType: Map[ProcessingType, ScenarioParametersWithEngineSetupErrors]
  ): Validated[ScenarioParametersConfigurationError, ScenarioParametersService] = {
    checkParametersToProcessingTypeMappingAmbiguity(parametersWithEngineErrorsForProcessingType) andThen
      (_ => checkProcessingModeCategoryWithInvalidEngineSetupsOnly(parametersWithEngineErrorsForProcessingType)) map {
        _ =>
          val parametersForProcessingType = parametersWithEngineErrorsForProcessingType.mapValuesNow(_.parameters)
          val engineSetupErrors           = deduplicateEngineSetupErrors(parametersWithEngineErrorsForProcessingType)
          new ScenarioParametersService(parametersForProcessingType, engineSetupErrors)
      }
  }

  private def checkParametersToProcessingTypeMappingAmbiguity(
      parametersCombinations: Map[ProcessingType, ScenarioParametersWithEngineSetupErrors]
  ): Validated[ScenarioParametersConfigurationError, Unit] = {
    val unambiguousParametersToProcessingTypeMappings = parametersCombinations.toList
      .map { case (processingType, parametersWithEngineErrors) =>
        parametersWithEngineErrors.parameters -> processingType
      }
      .toGroupedMap
      .filter(_._2.size > 1)
    if (unambiguousParametersToProcessingTypeMappings.nonEmpty)
      invalid(ParametersToProcessingTypeMappingAmbiguousException(unambiguousParametersToProcessingTypeMappings))
    else
      valid(())
  }

  // We decided to report this situation as an error during application startup because:
  // - On the FE side, we hide engine setup parameter when only one engine setup is available for all combinations of (processing mode, category)
  // - We don't want to have a complex logic on the FE side for the case when the only one available engine setups has errors -
  //   we can't just hide the engine setup parameter and report an error to the user
  // - The root cause why exposure the engine setup errors for users is to easy run image with the Embedded engine and missing Flink's restUrl in the configuration
  private def checkProcessingModeCategoryWithInvalidEngineSetupsOnly(
      parametersCombinations: Map[ProcessingType, ScenarioParametersWithEngineSetupErrors]
  ): Validated[ScenarioParametersConfigurationError, Unit] = {
    val combinationsWithEngineSetupErrorsOnly = parametersCombinations.toList
      .map { case (_, parametersWithEngineErrors) =>
        (
          parametersWithEngineErrors.parameters.processingMode,
          parametersWithEngineErrors.parameters.category
        ) -> parametersWithEngineErrors.engineSetupErrors
      }
      .toGroupedMap
      .collect {
        case (processingModeCategory, engineSetupErrors) if engineSetupErrors.forall(_.nonEmpty) =>
          processingModeCategory -> engineSetupErrors.flatten
      }
    if (combinationsWithEngineSetupErrorsOnly.nonEmpty)
      invalid(ProcessingModeCategoryWithInvalidEngineSetupsOnly(combinationsWithEngineSetupErrorsOnly))
    else
      valid(())
  }

  private def deduplicateEngineSetupErrors(
      parametersWithEngineErrorsForProcessingType: Map[ProcessingType, ScenarioParametersWithEngineSetupErrors]
  ) = {
    parametersWithEngineErrorsForProcessingType.toList
      .map { case (_, withErrors) =>
        withErrors.parameters.engineSetupName -> (withErrors.engineSetupErrors, Set(withErrors.parameters.category))
      }
      .toGroupedMapSafe
      .mapValuesNow(_.reduce)
      .mapValuesNow { case (errors, categories) =>
        ValueWithRestriction.userWithAccessRightsToAnyOfCategories(errors.distinct, categories)
      }
  }

}

sealed abstract class ScenarioParametersConfigurationError(message: String) extends Exception(message)

final case class ParametersToProcessingTypeMappingAmbiguousException(
    unambiguousMapping: Map[ScenarioParameters, List[ProcessingType]]
) extends ScenarioParametersConfigurationError(
      s"These scenario parameters are configured for more than one scenario type, which is not allowed now: $unambiguousMapping"
    )

final case class ProcessingModeCategoryWithInvalidEngineSetupsOnly(
    invalidParametersCombination: Map[(ProcessingMode, String), List[String]]
) extends ScenarioParametersConfigurationError(
      s"For the provided combinations of processing mode and category, only engine setups with errors are available: $invalidParametersCombination. " +
        "Please fix the scenarioType configuration"
    )

class ProcessingTypeNotFoundError(
    category: Option[String],
    processingMode: Option[ProcessingMode],
    engineSetupName: Option[EngineSetupName]
) extends BadRequestError(
      s"Processing type for combinations of parameters: category: $category, processing mode: $processingMode, engine setup: $engineSetupName not found"
    )

class MoreThanOneProcessingTypeFoundError(
    category: Option[String],
    processingMode: Option[ProcessingMode],
    engineSetupName: Option[EngineSetupName],
    processingTypes: Iterable[ProcessingType]
) extends BadRequestError(
      s"More than one processing type: ${processingTypes.mkString(", ")} for combinations of parameters: " +
        s"category: $category, processing mode: $processingMode, engine setup: $engineSetupName found"
    )
