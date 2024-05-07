package pl.touk.nussknacker.ui.process.version

import cats.data.EitherT
import db.util.DBIOActionInstances._
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.deployment.DMValidateScenarioCommand
import pl.touk.nussknacker.engine.api.{ProcessVersion => RuntimeVersionData}
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId}
import pl.touk.nussknacker.ui.db.entity.{ProcessEntityData, ProcessVersionEntityData}
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.deployment.{DeploymentManagerDispatcher, ScenarioResolver}
import pl.touk.nussknacker.ui.process.processingtype.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.UIProcessValidator
import slick.dbio.DBIOAction

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ScenarioGraphVersionService(
    scenarioGraphVersionRepository: ScenarioGraphVersionRepository,
    scenarioValidator: ProcessingTypeDataProvider[UIProcessValidator, _],
    scenarioResolver: ProcessingTypeDataProvider[ScenarioResolver, _],
    dmDispatcher: DeploymentManagerDispatcher
)(
    implicit ec: ExecutionContext
) {

  def getValidResolvedLatestScenarioGraphVersion(
      scenarioMetadata: ProcessEntityData
  )(implicit loggedUser: LoggedUser) = {
    for {
      scenarioGraphVersion <- EitherT.right(
        scenarioGraphVersionRepository.getLatestScenarioGraphVersion(scenarioMetadata.id)
      )
      _ <- EitherT(toEffectAll(DBIOAction.from(validate(scenarioMetadata, scenarioGraphVersion))))
    } yield ()
  }

  private def validate(scenarioMetadata: ProcessEntityData, scenarioGraphVersion: ProcessVersionEntityData)(
      implicit loggedUser: LoggedUser
  ): Future[Either[Unit, Unit]] = {
    (for {
      _ <- EitherT(Future {
        val validationResult = scenarioValidator
          .forProcessingTypeUnsafe(scenarioMetadata.processingType)
          .validateCanonicalProcess(scenarioGraphVersion.jsonUnsafe, scenarioMetadata.isFragment)
        // FIXME: error type
        Either.cond(validationResult.hasErrors, (), ())
      })
      _ <- validateUsingDeploymentManager(scenarioMetadata, scenarioGraphVersion)
    } yield ()).value
  }

  private def validateUsingDeploymentManager(
      scenarioMetadata: ProcessEntityData,
      scenarioGraphVersion: ProcessVersionEntityData
  )(
      implicit loggedUser: LoggedUser
  ): EitherT[Future, Unit, Unit] = {
    val runtimeVersionData = RuntimeVersionData(
      versionId = scenarioGraphVersion.id,
      processName = scenarioMetadata.name,
      processId = scenarioMetadata.id,
      user = scenarioGraphVersion.user,
      modelVersion = scenarioGraphVersion.modelVersion
    )
    // TODO: It shouldn't be needed
    val dumbDeploymentData = DeploymentData(
      DeploymentId(""),
      loggedUser.toManagerUser,
      Map.empty,
      NodesDeploymentData.empty
    )
    for {
      resolvedCanonicalProcess <- EitherT.right(
        Future.fromTry(
          scenarioResolver
            .forProcessingTypeUnsafe(scenarioMetadata.processingType)
            .resolveScenario(scenarioGraphVersion.jsonUnsafe)
        )
      )
      result <- EitherT(
        dmDispatcher
          .deploymentManagerUnsafe(scenarioMetadata.processingType)
          .processCommand(
            DMValidateScenarioCommand(
              runtimeVersionData,
              dumbDeploymentData,
              // TODO: scenario was already resolved during scenario validation - use it here
              resolvedCanonicalProcess
            )
          )
          .map(_ => Right(()))
          // FIXME: error type
          .recover { case NonFatal(_) =>
            Left(())
          }
      )
    } yield result
  }

}
