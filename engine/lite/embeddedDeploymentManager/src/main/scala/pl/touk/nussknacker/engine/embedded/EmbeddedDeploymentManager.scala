package pl.touk.nussknacker.engine.embedded

import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus.ProblemStateStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.deployment.{DeploymentData, DeploymentId, ExternalDeploymentId}
import pl.touk.nussknacker.engine.{ModelData, newdeployment}
import pl.touk.nussknacker.lite.manager.LiteDeploymentManager

import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

/*
  FIXME: better synchronization - comment below isn't true anymore + make HA ready
  Currently we assume that all operations that modify state (i.e. deploy and cancel) are performed from
  ManagementActor, which provides synchronization. Hence, we ignore all synchronization issues, except for
  checking status, but for this @volatile on interpreters should suffice.
 */
class EmbeddedDeploymentManager(
    override protected val modelData: ModelData,
    deployedScenariosProvider: ProcessingTypeDeployedScenariosProvider,
    deploymentStrategy: DeploymentStrategy
)(implicit ec: ExecutionContext)
    extends LiteDeploymentManager
    with LazyLogging
    with DeploymentManagerInconsistentStateHandlerMixIn {

  private val retrieveDeployedScenariosTimeout = 10.seconds

  @volatile private var deployments: Map[ProcessName, ScenarioDeploymentData] = {
    val deployedScenarios =
      Await.result(deployedScenariosProvider.getDeployedScenarios, retrieveDeployedScenariosTimeout)
    deployedScenarios
      .map(data =>
        deployScenario(
          data.processVersion,
          data.deploymentData,
          data.resolvedScenario,
          throwInterpreterRunExceptionsImmediately = false
        )
      )
      .toMap
  }

  override def processCommand[Result](command: DMScenarioCommand[Result]): Future[Result] =
    command match {
      case DMValidateScenarioCommand(_, _, _, updateStrategy) =>
        Future {
          ensureReplaceDeploymentUpdateStrategy(updateStrategy)
        }
      case DMRunDeploymentCommand(processVersion, deploymentData, canonicalProcess, updateStrategy) =>
        Future {
          ensureReplaceDeploymentUpdateStrategy(updateStrategy)
          ensureAdditionalComponentsConfigsAreEmpty(deploymentData)
          deployScenarioClosingOldIfNeeded(
            processVersion,
            deploymentData,
            canonicalProcess,
            throwInterpreterRunExceptionsImmediately = true
          )
        }
      case command: DMCancelDeploymentCommand => cancelDeployment(command)
      case command: DMCancelScenarioCommand   => cancelScenario(command)
      case command: DMTestScenarioCommand     => testScenario(command)
      case _: DMStopDeploymentCommand | _: DMStopScenarioCommand | _: DMMakeScenarioSavepointCommand |
          _: DMRunOffScheduleCommand =>
        notImplemented
    }

  private def ensureReplaceDeploymentUpdateStrategy(updateStrategy: DeploymentUpdateStrategy): Unit = {
    updateStrategy match {
      case DeploymentUpdateStrategy.ReplaceDeploymentWithSameScenarioName(_) =>
      case DeploymentUpdateStrategy.DontReplaceDeployment =>
        throw new IllegalArgumentException(s"Deployment update strategy: $updateStrategy is not supported")
    }
  }

  // We make sure that we don't let deploy a scenario when any component was modified by AdditionalUIConfigProvider
  // as it could potentially result in failure during compilation before execution
  private def ensureAdditionalComponentsConfigsAreEmpty(deploymentData: DeploymentData): Unit = {
    if (deploymentData.additionalModelConfigs.additionalConfigsFromProvider.nonEmpty) {
      throw new IllegalArgumentException(
        "Component config modification by AdditionalUIConfigProvider is not supported for Lite engine"
      )
    }
  }

  private def deployScenarioClosingOldIfNeeded(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      parsedResolvedScenario: CanonicalProcess,
      throwInterpreterRunExceptionsImmediately: Boolean
  ): Option[ExternalDeploymentId] = {
    deployments.get(processVersion.processName).collect {
      case ScenarioDeploymentData(_, processVersion, Success(oldVersion)) =>
        oldVersion.close()
        logger.debug(s"Closed already deployed scenario: $processVersion")
    }
    val deploymentEntry: (ProcessName, ScenarioDeploymentData) =
      deployScenario(processVersion, deploymentData, parsedResolvedScenario, throwInterpreterRunExceptionsImmediately)
    deployments += deploymentEntry
    Some(ExternalDeploymentId(deploymentEntry._2.deploymentId.value))
  }

  private def deployScenario(
      processVersion: ProcessVersion,
      deploymentData: DeploymentData,
      parsedResolvedScenario: CanonicalProcess,
      throwInterpreterRunExceptionsImmediately: Boolean
  ) = {

    val interpreterTry = runInterpreter(processVersion, parsedResolvedScenario)
    interpreterTry match {
      case Failure(ex) if throwInterpreterRunExceptionsImmediately =>
        throw ex
      case Failure(ex) =>
        logger.error("Exception during deploy scenario. Scenario will be in Failed state", ex)
      case Success(_) =>
        logger.debug(s"Deployed scenario $processVersion")
    }
    processVersion.processName -> ScenarioDeploymentData(deploymentData.deploymentId, processVersion, interpreterTry)
  }

  private def runInterpreter(processVersion: ProcessVersion, parsedResolvedScenario: CanonicalProcess) = {
    val jobData = JobData(parsedResolvedScenario.metaData, processVersion)
    deploymentStrategy.onScenarioAdded(jobData, parsedResolvedScenario)
  }

  private def cancelScenario(command: DMCancelScenarioCommand): Future[Unit] = {
    import command._
    deployments.get(scenarioName) match {
      case None                 => Future.failed(new IllegalArgumentException(s"Cannot find scenario $scenarioName"))
      case Some(deploymentData) => stopDeployment(scenarioName, deploymentData)
    }
  }

  private def cancelDeployment(command: DMCancelDeploymentCommand): Future[Unit] = {
    import command._
    for {
      deploymentData <- deployments
        .get(scenarioName)
        .map(Future.successful)
        .getOrElse(Future.failed(new IllegalArgumentException(s"Cannot find scenario $scenarioName")))
      deploymentDataForDeploymentId <- Option(deploymentData)
        .filter(_.deploymentId == deploymentId)
        .map(Future.successful)
        .getOrElse(
          Future.failed(
            new IllegalArgumentException(s"Cannot find deployment $deploymentId for scenario $scenarioName")
          )
        )
      stoppingResult <- stopDeployment(scenarioName, deploymentDataForDeploymentId)
    } yield stoppingResult
  }

  private def stopDeployment(name: ProcessName, deploymentData: ScenarioDeploymentData) = {
    Future.successful {
      deployments -= name
      deploymentData.scenarioDeployment.foreach { interpreter =>
        interpreter.close()
        logger.debug(s"Scenario $name stopped")
      }
    }
  }

  override def getProcessStates(
      name: ProcessName
  )(implicit freshnessPolicy: DataFreshnessPolicy): Future[WithDataFreshnessStatus[List[StatusDetails]]] = {
    Future.successful(
      WithDataFreshnessStatus.fresh(
        deployments
          .get(name)
          .map { interpreterData =>
            StatusDetails(
              status = interpreterData.scenarioDeployment
                .fold(
                  _ => ProblemStateStatus(s"Scenario compilation errors"),
                  deployment => SimpleStateStatus.fromDeploymentStatus(deployment.status())
                ),
              deploymentId = Some(interpreterData.deploymentId),
              externalDeploymentId = Some(ExternalDeploymentId(interpreterData.deploymentId.value)),
              version = Some(interpreterData.processVersion)
            )
          }
          .toList
      )
    )
  }

  override def deploymentSynchronisationSupport: DeploymentSynchronisationSupport =
    new DeploymentSynchronisationSupported {

      override def getDeploymentStatusesToUpdate(
          deploymentIdsToCheck: Set[newdeployment.DeploymentId]
      ): Future[Map[newdeployment.DeploymentId, DeploymentStatus]] =
        Future.successful(
          (
            for {
              (_, interpreterData) <- deployments.toList
              newDeployment        <- interpreterData.deploymentId.toNewDeploymentIdOpt
              status = interpreterData.scenarioDeployment
                .fold(_ => ProblemDeploymentStatus(s"Scenario compilation errors"), deployment => deployment.status())
            } yield newDeployment -> status
          ).toMap
        )

    }

  override def stateQueryForAllScenariosSupport: StateQueryForAllScenariosSupport = NoStateQueryForAllScenariosSupport

  override def schedulingSupport: SchedulingSupport = NoSchedulingSupport

  override def processStateDefinitionManager: ProcessStateDefinitionManager = EmbeddedProcessStateDefinitionManager

  override def close(): Unit = {
    deployments.values.foreach(_.scenarioDeployment.foreach(_.close()))
    deploymentStrategy.close()
    logger.info("All embedded scenarios successfully closed")
  }

  override protected def executionContext: ExecutionContext = ec

  private sealed case class ScenarioDeploymentData(
      deploymentId: DeploymentId,
      processVersion: ProcessVersion,
      scenarioDeployment: Try[Deployment]
  )

}
