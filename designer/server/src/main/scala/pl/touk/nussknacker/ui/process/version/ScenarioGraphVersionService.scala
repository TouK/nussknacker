package pl.touk.nussknacker.ui.process.version

import cats.data.{EitherT, Validated}
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.restmodel.validation.PrettyValidationErrors
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.process.ScenarioMetadata
import pl.touk.nussknacker.ui.process.deployment.ScenarioResolver
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.DBIOActionRunner
import pl.touk.nussknacker.ui.process.version.ScenarioGraphVersionService.ScenarioGraphValidationError
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.UIProcessValidator

import scala.concurrent.{ExecutionContext, Future}

class ScenarioGraphVersionService(
    scenarioGraphVersionRepository: ScenarioGraphVersionRepository,
    scenarioValidator: ProcessingTypeDataProvider[UIProcessValidator, _],
    scenarioResolver: ProcessingTypeDataProvider[ScenarioResolver, _],
    dbioRunner: DBIOActionRunner
)(implicit ec: ExecutionContext) {

  def getValidResolvedLatestScenarioGraphVersion(
      scenarioMetadata: ScenarioMetadata,
      user: LoggedUser
  ): Future[Either[ScenarioGraphValidationError, ProcessVersionEntityData]] = {
    (for {
      scenarioGraphVersion <- EitherT.right[ScenarioGraphValidationError](
        dbioRunner.run(
          scenarioGraphVersionRepository.getLatestScenarioGraphVersion(scenarioMetadata.id)
        )
      )
      processVersion = ProcessVersion(
        versionId = scenarioGraphVersion.id,
        processName = scenarioMetadata.name,
        processId = scenarioMetadata.id,
        labels = scenarioMetadata.labels.map(_.value),
        user = scenarioGraphVersion.user,
        modelVersion = scenarioGraphVersion.modelVersion,
      )
      _ <- EitherT.fromEither[Future] {
        val validationResult = scenarioValidator
          .forProcessingTypeUnsafe(scenarioMetadata.processingType)(user)
          .validateCanonicalProcess(scenarioGraphVersion.jsonUnsafe, processVersion, scenarioMetadata.isFragment)(user)
        // TODO: what about warnings?
        Either.cond(
          !validationResult.hasErrors,
          (),
          ScenarioGraphValidationError(
            validationResult.errors,
            validationResult.nodeNames.map { case (nodeId, nodeName) => NodeId(nodeId) -> nodeName }
          )
        )
      }
      // TODO: scenario was already resolved during validation - use it here
      resolvedCanonicalProcess <- EitherT(
        scenarioResolver
          .forProcessingTypeUnsafe(scenarioMetadata.processingType)(user)
          .resolveScenario(scenarioGraphVersion.jsonUnsafe)(user, ec)
          .map {
            case Validated.Valid(scenario) => Right(scenario)
            case Validated.Invalid(e) =>
              Left(
                ScenarioGraphValidationError(
                  ValidationErrors
                    .initial(processPropertiesErrors = e.map(PrettyValidationErrors.formatErrorMessage).toList)
                )
              )
          }
      )
      entityWithUpdateScenarioGraph = scenarioGraphVersion.copy(json = Some(resolvedCanonicalProcess))
    } yield entityWithUpdateScenarioGraph).value
  }

}

object ScenarioGraphVersionService {

  final case class ScenarioGraphValidationError(
      errors: ValidationErrors,
      nodeNamesById: Map[NodeId, String] = Map.empty
  )

}
