package pl.touk.nussknacker.engine.management

import java.io.File

import argonaut.PrettyParams
import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData.ClasspathConfig
import pl.touk.nussknacker.engine.{ModelData, ProcessManagerProvider, ProcessingTypeConfig}
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData, TypeSpecificData}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.deployment._

import scala.concurrent.{ExecutionContext, ExecutionContextExecutor, Future}

abstract class FlinkProcessManager(modelData: ModelData, shouldVerifyBeforeDeploy: Boolean) extends ProcessManager with LazyLogging {

  import argonaut.Argonaut._

  protected lazy val jarFile: File = new FlinkModelJar().buildJobJar(modelData)

  protected lazy val buildInfoJson: String = {
    modelData.configCreator.buildInfo().asJson.pretty(PrettyParams.spaces2.copy(preserveOrder = true))
  }

  private implicit val ec: ExecutionContextExecutor = ExecutionContext.Implicits.global

  private lazy val testRunner = new FlinkProcessTestRunner(modelData)

  private lazy val verification = new FlinkProcessVerifier(modelData)

  override def deploy(processVersion: ProcessVersion, processDeploymentData: ProcessDeploymentData, savepointPath: Option[String]): Future[Unit] = {
    val processId = processVersion.processId

    import cats.data.OptionT
    import cats.implicits._

    val stoppingResult = for {
      maybeOldJob <- OptionT(findJobStatus(processId).map(_.filter(_.isRunning)))
      maybeSavePoint <- {
        { logger.debug(s"Deploying $processId. Status: $maybeOldJob") }
        OptionT.liftF(stopSavingSavepoint(processVersion, maybeOldJob, processDeploymentData))
      }
    } yield {
      logger.info(s"Deploying $processId. Saving savepoint finished")
      maybeSavePoint
    }

    stoppingResult.value.flatMap { maybeSavepoint =>
      runProgram(processVersion.processId,
        prepareProgramMainClass(processDeploymentData),
        prepareProgramArgs(processVersion, processDeploymentData),
        savepointPath.orElse(maybeSavepoint))
    }
  }

  override def savepoint(processId: String, savepointDir: String): Future[String] = {
    findJobStatus(processId).flatMap {
      case Some(state) if state.isRunning =>
        makeSavepoint(state, Option(savepointDir))
      case Some(state) =>
        Future.failed(new IllegalStateException(s"Job $processId is not running, status: ${state.status}"))
      case None =>
        Future.failed(new IllegalStateException(s"Job $processId not found"))
    }
  }

  override def test[T](processId: String, processJson: String, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]] = {
    testRunner.test(processId, processJson, testData, variableEncoder)
  }

  override def cancel(name: String): Future[Unit] = {
    findJobStatus(name).flatMap {
      case Some(state) if state.isRunning =>
        cancel(state)
      case state =>
        logger.warn(s"Trying to cancel $name which is not running but in status: $state")
        Future.successful(())
    }
  }

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
        List(processVersion.processId, configPart, buildInfoJson)
    }
  }
  private def toJsonString(processVersion: ProcessVersion): String = {
    import argonaut.ArgonautShapeless._
    processVersion.asJson.spaces2
  }

  private def prepareProgramMainClass(processDeploymentData: ProcessDeploymentData) : String = {
    processDeploymentData match {
      case GraphProcess(_) => "pl.touk.nussknacker.engine.process.runner.FlinkProcessMain"
      case CustomProcess(mainClass) => mainClass
    }
  }

  protected def cancel(job: ProcessState): Future[Unit]

  protected def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[String]

  protected def runProgram(processId: String, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit]


}

class FlinkProcessManagerProvider extends ProcessManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.Ficus._


  override def createProcessManager(modelData: ModelData, config: Config): ProcessManager = {
    //FIXME: how to do it easier??
    val flinkConfig = ConfigFactory.empty().withValue("root", config.root()).as[FlinkConfig]("root")
    new FlinkRestManager(flinkConfig, modelData)
  }

  override def name: String = "flinkStreaming"

  override def emptyProcessMetadata(isSubprocess: Boolean): TypeSpecificData
    = StreamMetaData(parallelism = if (isSubprocess) None else Some(1))

  override def supportsSignals: Boolean = true

  override def supportsQueryableState: Boolean = true
}

object FlinkProcessManagerProvider {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.FicusReaders._

  def defaultTypeConfig(config: Config): ProcessingTypeConfig = {
    ProcessingTypeConfig("flinkStreaming",
      config.as[ClasspathConfig]("flinkConfig").urls,
      config.getConfig("flinkConfig"),
      config.getConfig("processConfig"))
  }

  def defaultModelData(config: Config): ModelData = defaultTypeConfig(config).toModelData

  def defaultProcessManager(config: Config): ProcessManager = {
    val typeConfig = defaultTypeConfig(config)
    new FlinkProcessManagerProvider().createProcessManager(typeConfig.toModelData, typeConfig.engineConfig)
  }

}