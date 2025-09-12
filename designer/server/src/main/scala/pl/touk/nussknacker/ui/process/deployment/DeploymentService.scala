package pl.touk.nussknacker.ui.process.deployment

import cats.data.Validated
import cats.effect.IO
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.component.{
  ComponentAdditionalConfig,
  DesignerWideComponentId,
  NodesDeploymentData,
  StaticParameterConfig
}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment._
import pl.touk.nussknacker.engine.util.{AdditionalComponentConfigsForRuntimeExtractor, ExecutionContextWithIORuntime}
import pl.touk.nussknacker.ui.limits.LimitsService
import pl.touk.nussknacker.ui.limits.LimitsService.LimitError.MaxActiveScenariosCountExceededError
import pl.touk.nussknacker.ui.process.deployment.LoggedUserConversions.LoggedUserOps
import pl.touk.nussknacker.ui.process.exception.DeployingInvalidScenarioError
import pl.touk.nussknacker.ui.process.livedata.LiveDataRepository
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
    limitsService: LimitsService,
    processingTypeToActionInfoService: ProcessingTypeDataProvider[ActionInfoService, _],
    liveDataRepository: LiveDataRepository,
    dbioActionRunner: DBIOActionRunner,
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
    val actionName = command match {
      case _: RunDeploymentCommand   => ScenarioActionName.Deploy
      case _: RunRedeploymentCommand => ScenarioActionName.Redeploy
    }
    actionProcessor
      .processActionWithCustomFinalization[T, RunDeploymentResult](
        command = command,
        actionName = actionName
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
            command,
            actionName,
          ).removeInvalidActionOnFailure()
          _ <- validateScenario(ctx.latestScenarioDetails).removeInvalidActionOnFailure()
          deploymentManager = dispatcher.deploymentManagerUnsafe(ctx.latestScenarioDetails.processingType)
          _ <- deploymentManager.liveDataPreviewSupport match {
            case LiveDataPreviewStoredInDesignerDb(_, _) =>
              dbioActionRunner.run(liveDataRepository.cleanLiveData(processIdWithName))
            case LiveDataPreviewStoredInDesignerJvm =>
              Future.unit
            case NoLiveDataPreviewSupport =>
              Future.unit
          }
          actionResult <- checkActiveScenariosLimits(ctx.latestScenarioDetails, dmCommand.updateStrategy) {
            IO.fromFuture {
              IO {
                for {
                  _ <- validateUsingDeploymentManager(ctx.latestScenarioDetails, dmCommand)
                    .removeInvalidActionOnFailure()
                } yield {
                  // we notify of deployment finish/fail only if initial validation succeeded - this step is done asynchronously
                  actionFinalizer.handleResult {
                    deploymentManager.processCommand(dmCommand)
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
      scenarioDetails: ScenarioWithDetailsEntity[CanonicalProcess],
      actionId: ProcessActionId,
      command: CommonDeploymentCommand,
      actionName: ScenarioActionName,
  )(implicit user: LoggedUser): Future[DMRunDeploymentCommand] = {
    for {
      resolvedCanonicalScenario <- scenarioResolver
        .forProcessingTypeUnsafe(scenarioDetails.processingType)
        .resolveScenario(scenarioDetails.json)
        .flatMap {
          case Validated.Valid(scenario) => Future.successful(scenario)
          case Validated.Invalid(e)      => Future.failed(new RuntimeException(e.head.toString))
        }
      nodesDeploymentData = prepareNodesDeploymentData(command, scenarioDetails, resolvedCanonicalScenario, actionName)
      deploymentData = DeploymentData(
        DeploymentId.fromActionId(actionId),
        user.toManagerUser,
        additionalDeploymentData = Map.empty,
        nodesDeploymentData,
        getAdditionalModelConfigsRequiredForRuntime(scenarioDetails.processingType)
      )
      updateStrategy = DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(
        command.stateRestoringStrategy
      )
      dmCommand = DMRunDeploymentCommand(
        scenarioDetails.toEngineProcessVersion,
        deploymentData,
        resolvedCanonicalScenario,
        updateStrategy
      )
    } yield dmCommand
  }

  private def prepareNodesDeploymentData(
      command: CommonDeploymentCommand,
      processDetails: ScenarioWithDetailsEntity[CanonicalProcess],
      resolvedCanonicalScenario: CanonicalProcess,
      actionName: ScenarioActionName
  )(implicit user: LoggedUser): NodesDeploymentData = {
    command.nodesDeploymentData.getOrElse {
      processingTypeToActionInfoService
        .forProcessingTypeUnsafe(processDetails.processingType)
        .getResolvedCanonicalScenarioActionParameters(
          resolvedCanonicalScenario,
          processDetails.toEngineProcessVersion,
        )
        .map { params =>
          params
            .get(actionName)
            .map { params =>
              val paramsMap = params.toList
                .map { case (nodeComponentInfo, paramsForNode) =>
                  nodeComponentInfo.nodeId -> paramsForNode
                    // Important NOTICE! If the user hasn't provided any (quick deploy/redeploy action) deploy parameters,
                    // we take parameters with defined default values.
                    // Parameters without default values (even required ones) are skipped.
                    .collect { case (paramName, StaticParameterConfig(Some(defaultValue), _, _, _, _)) =>
                      paramName.value -> defaultValue
                    }
                }
                .filterNot(_._2.isEmpty)
                .toMap
              NodesDeploymentData(paramsMap)
            }
            .getOrElse(NodesDeploymentData.empty)
        }
        .valueOr(_ => NodesDeploymentData.empty)
    }
  }

  private def getAdditionalModelConfigsRequiredForRuntime(processingType: ProcessingType)(implicit user: LoggedUser) = {
    AdditionalModelConfigs(
      AdditionalComponentConfigsForRuntimeExtractor.getRequiredAdditionalConfigsForRuntime(
        additionalComponentConfigs.forProcessingType(processingType).getOrElse(Map.empty)
      )
    )
  }

}
