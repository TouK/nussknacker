package pl.touk.nussknacker.engine.management

import java.io.File

import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.ProcessVersion
import pl.touk.nussknacker.engine.api.deployment.StateAction.StateAction
import pl.touk.nussknacker.engine.api.deployment.StateStatus.StateStatus
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName

import scala.concurrent.{ExecutionContext, Future}

abstract class FlinkProcessManager(modelData: ModelData, shouldVerifyBeforeDeploy: Boolean, mainClassName: String)
  extends ProcessManager with LazyLogging {

  protected lazy val jarFile: File = new FlinkModelJar().buildJobJar(modelData)

  protected lazy val buildInfoJson: String = {
    Encoder[Map[String, String]].apply(modelData.configCreator.buildInfo()).spaces2
  }

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  private lazy val testRunner = new FlinkProcessTestRunner(modelData)

  private lazy val verification = new FlinkProcessVerifier(modelData)

  override def deploy(processVersion: ProcessVersion, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]): Future[Unit] = {
    val processName = processVersion.processName

    import cats.data.OptionT
    import cats.implicits._

    val stoppingResult = for {
      oldJob <- OptionT(findStatusIgnoringTerminal(processName))
      _ <- OptionT[Future, Unit](if (!StateStatus.isRunning(oldJob))
        Future.failed(new IllegalStateException(s"Job ${processName.value} is not running, status: ${oldJob.status}")) else Future.successful(Some(())))
      maybeSavePoint <- {
        { logger.debug(s"Deploying $processName. Status: $oldJob") }
        OptionT.liftF(stopSavingSavepoint(processVersion, oldJob, processDeploymentData))
      }
    } yield {
      logger.info(s"Deploying $processName. Saving savepoint finished")
      maybeSavePoint
    }

    stoppingResult.value.flatMap { maybeSavepoint =>
      runProgram(processName,
        prepareProgramMainClass(processDeploymentData),
        prepareProgramArgs(processVersion, processDeploymentData),
        savepointPath.orElse(maybeSavepoint))
    }
  }

  override def savepoint(processName: ProcessName, savepointDir: String): Future[String] = {
    val name = processName.value
    findStatusIgnoringTerminal(processName).flatMap {
      case Some(state) if StateStatus.isRunning(state) =>
        makeSavepoint(state, Option(savepointDir))
      case Some(state) =>
        Future.failed(new IllegalStateException(s"Job $name is not running, status: ${state.status}"))
      case None =>
        Future.failed(new IllegalStateException(s"Job $name not found"))
    }
  }

  override def test[T](processName: ProcessName, processJson: String, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]] = {
    testRunner.test(processName, processJson, testData, variableEncoder)
  }

  override def cancel(processName: ProcessName): Future[Unit] = {
    findStatusIgnoringTerminal(processName).flatMap {
      case Some(state) if StateStatus.isRunning(state)=>
        cancel(state)
      case state =>
        logger.warn(s"Trying to cancel ${processName.value} which is not running but in status: $state")
        Future.successful(())
    }
  }

  private def findStatusIgnoringTerminal(processName: ProcessName): Future[Option[ProcessState]] =
    findJobStatus(processName).map(_.filterNot(StateStatus.isFinished))

  private def checkIfJobIsCompatible(savepointPath: String, processDeploymentData: ProcessDeploymentData, processVersion: ProcessVersion): Future[Unit] =
    processDeploymentData match {
      case GraphProcess(processAsJson) if shouldVerifyBeforeDeploy =>
        verification.verify(processVersion, processAsJson, savepointPath)
      case _ => Future.successful(())
    }


  private def stopSavingSavepoint(processVersion: ProcessVersion, job: ProcessState, processDeploymentData: ProcessDeploymentData): Future[String] = {
    for {
      savepointPath <- makeSavepoint(job, None)
      _ <- checkIfJobIsCompatible(savepointPath, processDeploymentData, processVersion)
      _ <- cancel(job)
    } yield savepointPath
  }

  private def prepareProgramArgs(processVersion: ProcessVersion, processDeploymentData: ProcessDeploymentData) : List[String] = {
    val configPart = modelData.processConfig.root().render()
    processDeploymentData match {
      case GraphProcess(processAsJson) =>
        List(processAsJson, toJsonString(processVersion), configPart, buildInfoJson)
      case CustomProcess(_) =>
        List(processVersion.processName.value, configPart, buildInfoJson)
    }
  }
  private def toJsonString(processVersion: ProcessVersion): String = {
    Encoder[ProcessVersion].apply(processVersion).spaces2
  }

  private def prepareProgramMainClass(processDeploymentData: ProcessDeploymentData) : String = {
    processDeploymentData match {
      case GraphProcess(_) => mainClassName
      case CustomProcess(mainClass) => mainClass
    }
  }

  protected def cancel(job: ProcessState): Future[Unit]

  protected def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[String]

  protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit]

  override def statusActions: Map[StateStatus, List[StateAction]] = ProcessStateCustoms.statusActions

  override def processStatePresenter: ProcessStatePresenter = ProcessStateCustomPresenter

  def getStatusActions(stateStatus: StateStatus): List[StateAction] = statusActions.getOrElse(stateStatus, List.empty)
}
