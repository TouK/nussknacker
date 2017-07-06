package pl.touk.esp.ui.initialization

import java.io.File
import java.util.Map.Entry

import _root_.db.migration.DefaultJdbcProfile
import com.typesafe.config.{ConfigFactory, ConfigValue}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.esp.ui.EspError
import pl.touk.esp.ui.EspError.XError
import pl.touk.esp.ui.db.EspTables
import pl.touk.esp.ui.db.entity.EnvironmentsEntity.EnvironmentsEntityData
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessType.ProcessType
import pl.touk.esp.ui.db.entity.ProcessEntity.{ProcessType, ProcessingType}
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.esp.ui.db.migration.SampleDataInserter
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import slick.jdbc.JdbcBackend

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source._
import scala.util

class Initialization(processRepository: ProcessRepository,
                     db: JdbcBackend.DatabaseDef,
                     environment: String,
                     initialProcessDirectory: File,
                     standaloneModeEnabled: Boolean) extends LazyLogging {

  implicit val toukUser = LoggedUser("TouK", "", List(Permission.Write, Permission.Admin), List())

  def insertInitialProcesses(): Unit = {

    insertInitialProcesses("processes", ProcessingType.Streaming)
    //TODO: standalone subprocesses?
    insertInitialProcesses("subprocesses", ProcessingType.Streaming, isSubprocess = true)

    updateTechnicalProcesses()
  }

  def insertStandaloneProcesses(): Unit = {
    if (standaloneModeEnabled) {
      insertInitialProcesses("standaloneProcesses", ProcessingType.RequestResponse)
    }
  }

  def insertInitialProcesses(dirName: String, processingType: ProcessingType, isSubprocess: Boolean = false): Unit = {
    val processesDir = new File(initialProcessDirectory, dirName)
    processesDir.mkdirs()
    processesDir.listFiles().filter(_.isDirectory).foreach { categoryDir =>
      val category = categoryDir.getName
      logger.info(s"Processing category $category")

      categoryDir.listFiles().foreach { file =>
        val processId = file.getName.replaceAll("\\..*", "")
        val processJson = fromFile(file).mkString
        saveOrUpdate(processId, category, GraphProcess(processJson), processingType, isSubprocess).map(removeFileOnSuccess(file, _))
      }
    }
  }

  def updateTechnicalProcesses(): Unit = {
    val customProcessesFile = new File(initialProcessDirectory, "customProcesses.conf")
    val futures = ConfigFactory.parseFile(customProcessesFile).entrySet().toSet
      .map { (entry: Entry[String, ConfigValue]) =>
        val processId = entry.getKey
        val deploymentData = CustomProcess(entry.getValue.unwrapped().toString)
        logger.info(s"Saving custom process $processId")
        saveOrUpdate(processId, "Technical", deploymentData, ProcessingType.Streaming, isSubprocess = false)
      }.toList
    Future.sequence(futures).foreach { potentialErrors =>
      val potentialError = potentialErrors.find(_.isLeft).getOrElse(util.Right(()))
      removeFileOnSuccess(customProcessesFile, potentialError)
    }
  }

  private def removeFileOnSuccess(file: File, xError: Either[EspError, Unit]) = xError match {
    case scala.util.Left(error) =>
      logger.warn(s"error $error occurred during processing of $file")
    case scala.util.Right(()) =>
      logger.info(s"processing file $file completed, removing file")
      file.delete()
  }

  private def saveOrUpdate(processId: String, category: String, deploymentData: ProcessDeploymentData,
                           processingType: ProcessingType, isSubprocess: Boolean): Future[XError[Unit]] = {
    val updateProcess = for {
      latestVersion <- processRepository.fetchLatestProcessVersion(processId)
      _ <- {
        latestVersion match {
          case None => processRepository.saveNewProcess(processId, category, deploymentData, processingType, isSubprocess)
          case Some(version) if version.user == toukUser.id => processRepository.updateProcess(processId, deploymentData)
          case _ => logger.info(s"Process $processId not updated. DB version is: \n${latestVersion.flatMap(_.json).getOrElse("")}\n " +
            s" and version from file is: \n$deploymentData")
                    Future.successful(Right(()))
        }
      }
      //this is non-transactional, but in initialization it should work just fine
    } yield processRepository.updateCategory(processId, category)
    Await.result(updateProcess, 1 second)
  }

  def insertEnvironment(environmentName: String) = {
    import DefaultJdbcProfile.profile.api._
    val insertAction = EspTables.environmentsTable += EnvironmentsEntityData(environmentName)
    db.run(insertAction).map(_ => ())
  }

}

object Initialization {

  def init(processRepository: ProcessRepository,
           db: JdbcBackend.DatabaseDef,
           environment: String,
           isDevelopmentMode: Boolean,
           initialProcessDirectory: File,
           standaloneModeEnabled: Boolean) = {
    val initialization = new Initialization(processRepository, db, environment, initialProcessDirectory, standaloneModeEnabled)
    initialization.insertInitialProcesses()
    initialization.insertStandaloneProcesses()
    initialization.insertEnvironment(environment)
    if (isDevelopmentMode) {
      SampleDataInserter.insert(db)
    }
  }


}
