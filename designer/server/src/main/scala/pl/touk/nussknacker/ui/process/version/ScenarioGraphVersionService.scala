package pl.touk.nussknacker.ui.process.version

import cats.data.EitherT
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationErrors
import pl.touk.nussknacker.ui.db.entity.{ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.process.deployment.ScenarioResolver
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.version.ScenarioGraphVersionService.ScenarioGraphValidationError
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.UIProcessValidator
import slick.dbio.DBIO
import db.util.DBIOActionInstances._

import scala.concurrent.{ExecutionContext, Future}

class ScenarioGraphVersionService(
    scenarioGraphVersionRepository: ScenarioGraphVersionRepository,
    scenarioValidator: ProcessingTypeDataProvider[UIProcessValidator, _],
    scenarioResolver: ProcessingTypeDataProvider[ScenarioResolver, _]
)(implicit ec: ExecutionContext) {

  def getValidResolvedLatestScenarioGraphVersion(
      scenarioMetadata: ProcessEntityData,
      user: LoggedUser
  ): DB[Either[ScenarioGraphValidationError, ProcessVersionEntityData]] = {
    (for {
      scenarioGraphVersion <- EitherT.right[ScenarioGraphValidationError](
        scenarioGraphVersionRepository.getLatestScenarioGraphVersion(scenarioMetadata.id)
      )
      _ <- EitherT.fromEither[DB] {
        val validationResult = scenarioValidator
          .forProcessingTypeUnsafe(scenarioMetadata.processingType)(user)
          .validateCanonicalProcess(scenarioGraphVersion.jsonUnsafe, scenarioMetadata.isFragment)(user)
        // TODO: what about warnings?
        Either.cond(!validationResult.hasErrors, (), ScenarioGraphValidationError(validationResult.errors))
      }
      // TODO: scenario was already resolved during validation - use it here
      resolvedCanonicalProcess <- EitherT.right[ScenarioGraphValidationError](
        toEffectAll(
          DBIO.from(
            Future.fromTry(
              scenarioResolver
                .forProcessingTypeUnsafe(scenarioMetadata.processingType)(user)
                .resolveScenario(scenarioGraphVersion.jsonUnsafe)(user)
            )
          )
        )
      )
      entityWithUpdateScenarioGraph = scenarioGraphVersion.copy(json = Some(resolvedCanonicalProcess))
    } yield entityWithUpdateScenarioGraph).value
  }

}

object ScenarioGraphVersionService {

  final case class ScenarioGraphValidationError(errors: ValidationErrors)

}
