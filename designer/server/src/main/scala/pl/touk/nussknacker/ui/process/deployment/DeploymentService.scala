package pl.touk.nussknacker.ui.process.deployment

import cats.data.Validated
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.component.{ComponentAdditionalConfig, DesignerWideComponentId}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment._
import pl.touk.nussknacker.engine.util.{AdditionalComponentConfigsForRuntimeExtractor, ExecutionContextWithIORuntime}
import pl.touk.nussknacker.ui.limits.LimitsService
import pl.touk.nussknacker.ui.limits.LimitsService.LimitError.MaxActiveScenariosCountExceededError
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.exception.DeployingInvalidScenarioError
import pl.touk.nussknacker.ui.process.processingtype.provider.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository._
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.validation.UIProcessValidator

import scala.concurrent.Future
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
    limitsService: LimitsService
)(implicit executionContextWithIORuntime: ExecutionContextWithIORuntime)
    extends LazyLogging {

  import executionContextWithIORuntime.ioRuntime

  def processCommand[Result](command: ScenarioCommand[Result]): Future[Result] = {
    command match {
      case command: RunDeploymentCommand   => runDeploymentOrRedeploy(command)
      case command: RunRedeploymentCommand => runDeploymentOrRedeploy(command)
      case command: CancelScenarioCommand  => cancelScenario(command)
      case command: RunOffScheduleCommand  => runOffSchedule(command)
    }
  }

  private def cancelScenario(command: CancelScenarioCommand): Future[Unit] = {
    // During cancel we refer to the version that is deployed (see lastDeployedAction). In some cases, when action fails
    // and deployment continues on flink, lastDeployedAction is empty. Then we allow cancel action to proceed, to cancel
    // a running job. In that case there is no deploy action and action cancel is removed.
    // TODO: This inconsistent action-state handling needs a fix.
    actionService
      .actionProcessorForVersion[Unit](_.lastDeployedAction.map(_.processVersionId), LatestVersion)
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

  private def runDeploymentOrRedeploy[T <: CommonDeploymentCommand with ScenarioCommand[RunDeploymentResult]](
      command: T
  ): Future[RunDeploymentResult] = {
    import command.commonData._
    val actionProcessor = command.scenarioSource match {
      case LatestVersion     => actionService.actionProcessorForLatestVersion[CanonicalProcess]
      case source: FromGraph => actionService.actionProcessorForScenarioGraph[CanonicalProcess](source)
    }
    actionProcessor
      .processActionWithCustomFinalization[T, RunDeploymentResult](
        command = command,
        actionName = command match {
          case _: RunDeploymentCommand   => ScenarioActionName.Deploy
          case _: RunRedeploymentCommand => ScenarioActionName.Redeploy
        }
      ) { case (ctx, actionFinalizer) =>
        implicit class FinalizerExt[T](val future: Future[T]) {
          def removeInvalidActionOnFailure(): Future[T] = {
            future.transformWith {
              case Success(result) => Future.successful(result)
              case Failure(ex)     => actionFinalizer.removeInvalidAction().transform(_ => Failure(ex))
            }
          }
        }

        for {
          dmCommand <- prepareDMRunDeploymentCommand(
            ctx.latestScenarioDetails,
            ctx.actionId,
            // TODO: We should validate node deployment data - e.g. if sql expression is a correct sql expression,
            //       references to existing fields and uses correct types. We should also protect from sql injection attacks
            command
          ).removeInvalidActionOnFailure()
          _ <- validateScenario(ctx.latestScenarioDetails).removeInvalidActionOnFailure()
          actionResult <- checkActiveScenariosLimits(ctx.latestScenarioDetails, dmCommand.updateStrategy) {
            IO.fromFuture {
              IO {
                for {
                  _ <- validateUsingDeploymentManager(ctx.latestScenarioDetails, dmCommand)
                    .removeInvalidActionOnFailure()
                } yield {
                  // we notify of deployment finish/fail only if initial validation succeeded - this step is done asynchronously
                  actionFinalizer.handleResult {
                    dispatcher
                      .deploymentManagerUnsafe(ctx.latestScenarioDetails.processingType)
                      .processCommand(dmCommand)
                  }
                }
              }
            }
          }
            .flatMap {
              case Right(result) =>
                Future.successful(
                  RunDeploymentResult(
                    result,
                    ctx.latestScenarioDetails.processVersionId
                  )
                )
              case Left(error: MaxActiveScenariosCountExceededError) =>
                Future.failed(error).removeInvalidActionOnFailure()
            }
        } yield actionResult
      }
  }

  private def validateScenario(
      scenarioDetails: ScenarioWithDetailsEntity[CanonicalProcess]
  )(implicit user: LoggedUser): Future[Unit] = Future {
    processValidator
      .forProcessingTypeUnsafe(scenarioDetails.processingType)
      .validateCanonicalProcess(
        scenarioDetails.json,
        scenarioDetails.toEngineProcessVersion,
        scenarioDetails.isFragment
      ) match {
      case validationResult if validationResult.hasErrors =>
        throw DeployingInvalidScenarioError(validationResult.errors)
      case _ => ()
    }
  }

  private def checkActiveScenariosLimits(
      scenario: ScenarioWithDetailsEntity[CanonicalProcess],
      deploymentUpdateStrategy: DeploymentUpdateStrategy,
  )(action: IO[Future[Option[ExternalDeploymentId]]])(implicit user: LoggedUser) = {
    limitsService
      .checkActiveScenarioLimitsBeforeDeployment(scenario.name, scenario.processingType, deploymentUpdateStrategy)(
        action
      )
      .unsafeToFuture()
  }

  protected def validateUsingDeploymentManager(
      scenarioDetails: ScenarioWithDetailsEntity[CanonicalProcess],
      runDeploymentCommand: DMRunDeploymentCommand,
  )(implicit user: LoggedUser): Future[Unit] = {
    dispatcher
      .deploymentManagerUnsafe(scenarioDetails.processingType)
      .processCommand(
        DMValidateScenarioCommand(
          runDeploymentCommand.processVersion,
          runDeploymentCommand.deploymentData,
          runDeploymentCommand.canonicalProcess,
          runDeploymentCommand.updateStrategy
        )
      )
  }

  private def prepareDMRunDeploymentCommand(
      processDetails: ScenarioWithDetailsEntity[CanonicalProcess],
      actionId: ProcessActionId,
      command: CommonDeploymentCommand,
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
