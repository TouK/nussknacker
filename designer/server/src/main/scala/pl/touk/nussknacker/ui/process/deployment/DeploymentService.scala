package pl.touk.nussknacker.ui.process.deployment

import cats.data.Validated
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ProcessingTypeConfig.ActiveScenariosLimit
import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment._
import pl.touk.nussknacker.engine.util.AdditionalComponentConfigsForRuntimeExtractor
import pl.touk.nussknacker.ui.process.deployment.DeploymentService.ActiveScenariosLimitExceededError
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.deployment.scenariostatus.ScenarioStatusProvider
import pl.touk.nussknacker.ui.process.exception.DeployingInvalidScenarioError
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.UIProcessValidator

import scala.concurrent.{ExecutionContext, Future}
import scala.language.higherKinds
import scala.util.{Failure, Success}

// TODO: This class will be replaced by DeploymentService from newdeployment package, see docs there
class DeploymentService(
    dispatcher: DeploymentManagerDispatcher,
    processValidator: ProcessingTypeDataProvider[UIProcessValidator, _],
    scenarioResolver: ProcessingTypeDataProvider[ScenarioResolver, _],
    actionService: ActionService,
    additionalComponentConfigs: ProcessingTypeDataProvider[
      Map[DesignerWideComponentId, ComponentAdditionalConfig],
      _
    ],
    scenarioStatusProvider: ScenarioStatusProvider,
    activeScenariosLimitProvider: ProcessingTypeDataProvider[Option[ActiveScenariosLimit], _],
)(implicit ec: ExecutionContext)
    extends LazyLogging {

  def processCommand[Result](command: ScenarioCommand[Result]): Future[Result] = {
    command match {
      case command: RunDeploymentCommand  => runDeployment(command)
      case command: CancelScenarioCommand => cancelScenario(command)
      case command: RunOffScheduleCommand => runOffSchedule(command)
    }
  }

  private def cancelScenario(command: CancelScenarioCommand): Future[Unit] = {
    // During cancel we refer to the version that is deployed (see lastDeployedAction). In some cases, when action fails
    // and deployment continues on flink, lastDeployedAction is empty. Then we allow cancel action to proceed, to cancel
    // a running job. In that case there is no deploy action and action cancel is removed.
    // TODO: This inconsistent action-state handling needs a fix.
    actionService
      .actionProcessorForVersion[Unit](_.lastDeployedAction.map(_.processVersionId))
      .processAction[CancelScenarioCommand, Unit](command = command, actionName = ScenarioActionName.Cancel) { ctx =>
        import command.commonData._
        dispatcher
          .deploymentManagerUnsafe(ctx.latestScenarioDetails.processingType)
          .processCommand(
            DMCancelScenarioCommand(command.commonData.processIdWithName.name, command.commonData.user.toManagerUser)
          )
      }
  }

  private def runOffSchedule(command: RunOffScheduleCommand): Future[RunOffScheduleResult] = {
    actionService
      .actionProcessorForLatestVersion[CanonicalProcess]
      .processAction[RunOffScheduleCommand, RunOffScheduleResult](
        command = command,
        actionName = ScenarioActionName.RunOffSchedule
      ) { ctx =>
        import command.commonData._
        dispatcher
          .deploymentManagerUnsafe(ctx.latestScenarioDetails.processingType)
          .processCommand(
            DMRunOffScheduleCommand(
              ctx.latestScenarioDetails.toEngineProcessVersion,
              ctx.latestScenarioDetails.json,
              command.commonData.user.toManagerUser,
            )
          )
      }
  }

  private def runDeployment(command: RunDeploymentCommand): Future[Future[Option[ExternalDeploymentId]]] = {
    import command.commonData._
    actionService
      .actionProcessorForLatestVersion[CanonicalProcess]
      .processActionWithCustomFinalization[RunDeploymentCommand, Future[Option[ExternalDeploymentId]]](
        command = command,
        actionName = ScenarioActionName.Deploy
      ) { case (ctx, actionFinalizer) =>
        for {
          dmCommand <- prepareDMRunDeploymentCommand(
            ctx.latestScenarioDetails,
            ctx.actionId,
            // TODO: We should validate node deployment data - e.g. if sql expression is a correct sql expression,
            //       references to existing fields and uses correct types. We should also protect from sql injection attacks
            command
          )
          // TODO: move validateBeforeDeploy before creating an action
          actionResult <- validateBeforeDeploy(ctx.latestScenarioDetails, dmCommand)
            .transformWith {
              case Failure(ex) =>
                actionFinalizer.removeInvalidAction().transform(_ => Failure(ex))
              case Success(_) =>
                // we notify of deployment finish/fail only if initial validation succeeded - this step is done asynchronously
                Future {
                  actionFinalizer.handleResult {
                    dispatcher
                      .deploymentManagerUnsafe(ctx.latestScenarioDetails.processingType)
                      .processCommand(dmCommand)
                  }
                }
            }
        } yield actionResult
      }
  }

  protected def validateBeforeDeploy(
      processDetails: ScenarioWithDetailsEntity[CanonicalProcess],
      runDeploymentCommand: DMRunDeploymentCommand,
  )(implicit user: LoggedUser): Future[Unit] = {
    for {
      // 1. check scenario has no errors
      _ <- Future {
        processValidator
          .forProcessingTypeUnsafe(processDetails.processingType)
          .validateCanonicalProcess(
            processDetails.json,
            processDetails.toEngineProcessVersion,
            processDetails.isFragment
          )
      }.flatMap {
        case validationResult if validationResult.hasErrors =>
          Future.failed(DeployingInvalidScenarioError(validationResult.errors))
        case _ => Future.successful(())
      }
      // 2. limits check
      _ <- checkActiveScenariosLimits(processDetails.processingType, user)
      // 3. deployment managers specific checks
      // TODO: scenario was already resolved during validation - use it here
      _ <- dispatcher
        .deploymentManagerUnsafe(processDetails.processingType)
        .processCommand(
          DMValidateScenarioCommand(
            runDeploymentCommand.processVersion,
            runDeploymentCommand.deploymentData,
            runDeploymentCommand.canonicalProcess,
            runDeploymentCommand.updateStrategy
          )
        )
    } yield ()
  }

  private def checkActiveScenariosLimits(processingType: ProcessingType, deployer: LoggedUser) = {
    implicit val loggedUser: LoggedUser = deployer
    activeScenariosLimitProvider.forProcessingType(processingType).flatten match {
      case Some(ActiveScenariosLimit(activeScenariosLimit)) =>
        scenarioStatusProvider
          .getActiveScenariosCountFor(processingType)
          .map { activeScenariosCount =>
            if (activeScenariosCount >= activeScenariosLimit) {
              throw ActiveScenariosLimitExceededError(activeScenariosLimit)
            }
          }
      case None =>
        Future.unit
    }
  }

  private def prepareDMRunDeploymentCommand(
      processDetails: ScenarioWithDetailsEntity[CanonicalProcess],
      actionId: ProcessActionId,
      command: RunDeploymentCommand,
  )(implicit user: LoggedUser): Future[DMRunDeploymentCommand] = {
    for {
      resolvedCanonicalProcess <- scenarioResolver
        .forProcessingTypeUnsafe(processDetails.processingType)
        .resolveScenario(processDetails.json)
        .flatMap {
          case Validated.Valid(scenario) => Future.successful(scenario)
          case Validated.Invalid(e)      => Future.failed(new RuntimeException(e.head.toString))
        }
      deploymentData = DeploymentData(
        DeploymentId.fromActionId(actionId),
        user.toManagerUser,
        additionalDeploymentData = Map.empty,
        command.nodesDeploymentData,
        getAdditionalModelConfigsRequiredForRuntime(processDetails.processingType)
      )
      updateStrategy = DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
        command.stateRestoringStrategy
      )
      dmCommand = DMRunDeploymentCommand(
        processDetails.toEngineProcessVersion,
        deploymentData,
        resolvedCanonicalProcess,
        updateStrategy
      )
    } yield dmCommand
  }

  private def getAdditionalModelConfigsRequiredForRuntime(processingType: ProcessingType)(implicit user: LoggedUser) = {
    AdditionalModelConfigs(
      AdditionalComponentConfigsForRuntimeExtractor.getRequiredAdditionalConfigsForRuntime(
        additionalComponentConfigs.forProcessingType(processingType).getOrElse(Map.empty)
      )
    )
  }

}

object DeploymentService {

  final case class ActiveScenariosLimitExceededError(activeScenariosLimit: Int)
      extends IllegalArgumentException(
        s"The limit of active scenarios has been reached. You can have a maximum of $activeScenariosLimit active scenarios."
      )

}
