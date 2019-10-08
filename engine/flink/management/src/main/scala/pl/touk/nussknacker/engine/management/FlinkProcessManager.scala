package pl.touk.nussknacker.engine.management

import java.io.File

import com.typesafe.config.{Config, ConfigFactory}
import com.typesafe.scalalogging.LazyLogging
import io.circe.Encoder
import pl.touk.nussknacker.engine.ModelData.ClasspathConfig
import pl.touk.nussknacker.engine.{ModelData, ProcessManagerProvider, ProcessingTypeConfig}
import pl.touk.nussknacker.engine.api.{ProcessVersion, StreamMetaData, TypeSpecificData}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.flink.queryablestate.FlinkQueryableClient
import pl.touk.nussknacker.engine.queryablestate.QueryableClient

import scala.concurrent.{ExecutionContext, Future}

abstract class FlinkProcessManager(modelData: ModelData, shouldVerifyBeforeDeploy: Boolean) extends ProcessManager with LazyLogging {

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
      _ <- OptionT[Future, Unit](if (!(oldJob.runningState == RunningState.Running))
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
      case Some(state) if state.runningState == RunningState.Running =>
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
      case Some(state) if state.runningState == RunningState.Running =>
        cancel(state)
      case state =>
        logger.warn(s"Trying to cancel ${processName.value} which is not running but in status: $state")
        Future.successful(())
    }
  }

  private def findStatusIgnoringTerminal(processName: ProcessName): Future[Option[ProcessState]]
  = findJobStatus(processName).map(_.filterNot(status => status.runningState == RunningState.Finished))

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
      case GraphProcess(_) => "pl.touk.nussknacker.engine.process.runner.FlinkProcessMain"
      case CustomProcess(mainClass) => mainClass
    }
  }

  protected def cancel(job: ProcessState): Future[Unit]

  protected def makeSavepoint(job: ProcessState, savepointDir: Option[String]): Future[String]

  protected def runProgram(processName: ProcessName, mainClass: String, args: List[String], savepointPath: Option[String]): Future[Unit]


}

class FlinkProcessManagerProvider extends ProcessManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import net.ceedubs.ficus.Ficus._


  override def createProcessManager(modelData: ModelData, config: Config): ProcessManager = {
    //FIXME: how to do it easier??
    val flinkConfig = asFlinkConfig(config)
    new FlinkRestManager(flinkConfig, modelData)
  }

  override def createQueryableClient(config: Config): Option[QueryableClient] = {
    val flinkConfig = asFlinkConfig(config)
    flinkConfig.queryableStateProxyUrl.map(FlinkQueryableClient(_))
  }

  override def name: String = "flinkStreaming"

  override def emptyProcessMetadata(isSubprocess: Boolean): TypeSpecificData
    = StreamMetaData(parallelism = if (isSubprocess) None else Some(1))

  override def supportsSignals: Boolean = true

  private def asFlinkConfig(config: Config): FlinkConfig = {
    //FIXME: how to do it easier??
    ConfigFactory.empty().withValue("root", config.root()).as[FlinkConfig]("root")
  }
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