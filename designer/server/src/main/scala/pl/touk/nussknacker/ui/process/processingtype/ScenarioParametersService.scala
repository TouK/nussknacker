package pl.touk.nussknacker.ui.process.processingtype

import cats.data.Validated
import cats.data.Validated.{invalid, valid}
import pl.touk.nussknacker.engine.api.component.ProcessingMode
import pl.touk.nussknacker.engine.api.process.ProcessingType
import pl.touk.nussknacker.engine.deployment.EngineSetupName
import pl.touk.nussknacker.engine.util.Implicits.RichTupleList
import pl.touk.nussknacker.restmodel.scenariodetails.{ScenarioParameters, ScenarioParametersWithEngineSetupErrors}
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.{NotFoundError, NuDesignerError, UnauthorizedError}

class ScenarioParametersService private (
    parametersForProcessingType: Map[ProcessingType, ScenarioParametersWithEngineSetupErrors]
) {

  def scenarioParametersCombinationsWithWritePermission(
      implicit loggedUser: LoggedUser
  ): List[ScenarioParametersWithEngineSetupErrors] =
    parametersForProcessingType.values.toList
      .filter { parametersWithEngineErrors =>
        loggedUser.can(parametersWithEngineErrors.parameters.category, Permission.Write)
      }

  // We decided to not show all parameters except processing mode when they are not needed to pick
  // the correct processing type. Because of that, all parameters are optional
  def getProcessingTypeWithWritePermission(
      category: Option[String],
      processingMode: Option[ProcessingMode],
      engineSetupName: Option[EngineSetupName],
  )(implicit loggedUser: LoggedUser): Validated[NuDesignerError, ProcessingType] = {
    if (!category.forall(loggedUser.can(_, Permission.Write))) {
      invalid(new UnauthorizedError("User doesn't have access to the given category"))
    } else {
      val matchingProcessingTypes = parametersForProcessingType.toList.collect {
        case (processingType, parametersWithEngineErrors)
            if processingMode.forall(_ == parametersWithEngineErrors.parameters.processingMode) &&
              engineSetupName.forall(_ == parametersWithEngineErrors.parameters.engineSetupName) &&
              category.forall(_ == parametersWithEngineErrors.parameters.category) =>
          processingType
      }
      matchingProcessingTypes match {
        case singleProcessingType :: Nil => valid(singleProcessingType)
        case Nil => invalid(new ProcessingTypeNotFoundError(category, processingMode, engineSetupName))
        case moreThanOne =>
          throw new IllegalStateException(
            s"ScenarioParametersCombination to processing type mapping should be 1-to-1 but for parameters: " +
              s"$category, $processingMode, $engineSetupName there is more than one processingType: ${moreThanOne.mkString(", ")}"
          )
      }
    }
  }

  // TODO: This method is for legacy API purpose, it will be removed in 1.15
  def categoryUnsafe(processingType: ProcessingType): String =
    parametersForProcessingType
      .get(processingType)
      .map(parametersWithEngineErrors => parametersWithEngineErrors.parameters.category)
      .getOrElse(throw new IllegalStateException(s"Processing type: $processingType not found"))

  def getParametersWithReadPermissionUnsafe(
      processingType: ProcessingType
  )(implicit loggedUser: LoggedUser): ScenarioParameters = {
    val parameters = parametersForProcessingType(processingType).parameters
    if (!loggedUser.can(parameters.category, Permission.Read)) {
      throw new UnauthorizedError("User doesn't have access to the given category")
    } else {
      parameters
    }
  }

}

object ScenarioParametersService {

  def createUnsafe(
      parametersForProcessingType: Map[ProcessingType, ScenarioParametersWithEngineSetupErrors]
  ): ScenarioParametersService =
    create(parametersForProcessingType).valueOr(ex => throw ex)

  def create(
      parametersForProcessingType: Map[ProcessingType, ScenarioParametersWithEngineSetupErrors]
  ): Validated[ParametersToProcessingTypeMappingAmbiguousException, ScenarioParametersService] = {
    checkParametersToProcessingTypeMappingAmbiguity(parametersForProcessingType).map(_ =>
      new ScenarioParametersService(parametersForProcessingType)
    )
  }

  private def checkParametersToProcessingTypeMappingAmbiguity(
      parametersCombinations: Map[ProcessingType, ScenarioParametersWithEngineSetupErrors]
  ): Validated[ParametersToProcessingTypeMappingAmbiguousException, Unit] = {
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

}

final case class ParametersToProcessingTypeMappingAmbiguousException(
    unambiguousMapping: Map[ScenarioParameters, List[ProcessingType]]
) extends IllegalStateException(
      s"These scenario parameters are configured for more than one scenario type, which is not allowed now: $unambiguousMapping"
    )

class ProcessingTypeNotFoundError(
    category: Option[String],
    processingMode: Option[ProcessingMode],
    engineSetupName: Option[EngineSetupName]
) extends NotFoundError(
      s"Processing type for combinations of parameters: category: $category, processing mode: $processingMode, engine setup: $engineSetupName"
    )
