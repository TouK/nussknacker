package pl.touk.nussknacker.ui.initialization

import java.io.File
import java.util.Map.Entry

import _root_.db.migration.DefaultJdbcProfile
import com.typesafe.config.{ConfigFactory, ConfigValue}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData}
import pl.touk.nussknacker.ui.EspError
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui.db.EspTables
import pl.touk.nussknacker.ui.db.entity.EnvironmentsEntity.EnvironmentsEntityData
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.db.migration.SampleDataInserter
import pl.touk.nussknacker.ui.process.migrate.ProcessModelMigrator
import pl.touk.nussknacker.ui.process.repository.{ProcessActivityRepository, ProcessRepository}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import slick.jdbc.JdbcBackend

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.io.Source._
import scala.util

class Initialization(processRepository: ProcessRepository,
                     db: JdbcBackend.DatabaseDef,
                     initialProcessDirectory: File) extends LazyLogging {

  private implicit val user = Initialization.toukUser

  def insertInitialProcesses(): Future[Any] = {

    Future.sequence(List(
      insertInitialProcesses("processes", ProcessingType.Streaming),
      //TODO: standalone subprocesses?
      insertInitialProcesses("subprocesses", ProcessingType.Streaming, isSubprocess = true),
      updateTechnicalProcesses()))
  }

  def insertInitialProcesses(dirName: String, processingType: ProcessingType, isSubprocess: Boolean = false): Future[Any] = {
    val processesDir = new File(initialProcessDirectory, dirName)
    processesDir.mkdirs()
    val result = processesDir.listFiles().filter(_.isDirectory).flatMap { categoryDir =>
      val category = categoryDir.getName
      logger.info(s"Processing category $category")

      categoryDir.listFiles().map { file =>
        val processId = file.getName.replaceAll("\\..*", "")
        val processJson = fromFile(file).mkString
        saveOrUpdate(processId, category, GraphProcess(processJson), processingType, isSubprocess).map(removeFileOnSuccess(file, _))
      }
    }.toList
    
    Future.sequence(result)
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
          case Some(version) if version.user == Initialization.toukUser.id => processRepository.updateProcess(processId, deploymentData)
          case _ => logger.info(s"Process $processId not updated. DB version is: \n${latestVersion.flatMap(_.json).getOrElse("")}\n " +
            s" and version from file is: \n$deploymentData")
            Future.successful(Right(()))
        }
      }
    //this is non-transactional, but in initialization it should work just fine
    } yield processRepository.updateCategory(processId, category)
    Await.result(updateProcess, 1 second)
  }

  def updateTechnicalProcesses(): Future[Any] = {
    val customProcessesFile = new File(initialProcessDirectory, "customProcesses.conf")
    val futures = ConfigFactory.parseFile(customProcessesFile).entrySet().toSet
      .map { (entry: Entry[String, ConfigValue]) =>
        val processId = entry.getKey
        val deploymentData = CustomProcess(entry.getValue.unwrapped().toString)
        logger.info(s"Saving custom process $processId")
        saveOrUpdate(processId, "Technical", deploymentData, ProcessingType.Streaming, isSubprocess = false)
      }.toList
    val result = Future.sequence(futures)
    result.foreach { potentialErrors =>
      val potentialError = potentialErrors.find(_.isLeft).getOrElse(util.Right(()))
      removeFileOnSuccess(customProcessesFile, potentialError)
    }
    result
  }

  def insertStandaloneProcesses(): Future[Any] = {
    insertInitialProcesses("standaloneProcesses", ProcessingType.RequestResponse)
  }

  def insertEnvironment(environmentName: String): Future[Unit] = {
    import DefaultJdbcProfile.profile.api._
    val insertAction = EspTables.environmentsTable += EnvironmentsEntityData(environmentName)
    db.run(insertAction).map(_ => ())
  }

}

object Initialization {

  implicit val toukUser = LoggedUser("TouK", "", List(Permission.Write, Permission.Admin), List())


  def init(modelData: Map[ProcessingType, ModelData],
           processRepository: ProcessRepository, processActivityRepository: ProcessActivityRepository,
           db: JdbcBackend.DatabaseDef,
           environment: String,
           isDevelopmentMode: Boolean,
           initialProcessDirectory: File) : Unit = {
    val initialization = new Initialization(processRepository, db, initialProcessDirectory)

    val initializationResult = for {
      _ <- initialization.insertInitialProcesses()
      _ <- if (modelData.contains(ProcessingType.RequestResponse)) {
        initialization.insertStandaloneProcesses()
      } else {
        Future.successful(())
      }
      _ <- initialization.insertEnvironment(environment)
      _ <- ProcessModelMigrator(processRepository, processActivityRepository, modelData).migrate(toukUser, ExecutionContext.Implicits.global)
    } yield ()

    Await.ready(initializationResult, 10 seconds)

    if (isDevelopmentMode) {
      SampleDataInserter.insert(db)
    }
  }



}
