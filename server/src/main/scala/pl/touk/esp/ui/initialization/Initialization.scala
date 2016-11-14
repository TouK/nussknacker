package pl.touk.esp.ui.initialization

import java.io.File
import java.util.Map.Entry

import _root_.db.migration.DefaultJdbcProfile
import cats.data.Xor
import com.typesafe.config.{ConfigFactory, ConfigValue}
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess}
import pl.touk.esp.ui.db.EspTables
import pl.touk.esp.ui.db.entity.EnvironmentsEntity.EnvironmentsEntityData
import pl.touk.esp.ui.db.migration.SampleDataInserter
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import slick.jdbc.JdbcBackend

import scala.collection.JavaConversions._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.io.Source._

class Initialization(processRepository: ProcessRepository,
                     db: JdbcBackend.DatabaseDef, environment: String, initialProcessDirectory: File) extends LazyLogging {

  implicit val toukUser = LoggedUser("TouK", "", List(Permission.Write, Permission.Admin), List())

  def insertInitialProcesses(): Unit = {

    new File(initialProcessDirectory, "processes").listFiles().filter(_.isDirectory).foreach { categoryDir =>
      val category = categoryDir.getName
      logger.info(s"Processing category $category")

      categoryDir.listFiles().foreach { file =>
        val name = file.getName.replaceAll("\\..*", "")
        val updateProcess = for {
          latestVersion <- processRepository.fetchLatestProcessVersion(name)
          versionShouldBeUpdated = latestVersion.exists(_.user == toukUser.id) || latestVersion.isEmpty
          processJson = fromFile(file).mkString
          _ <- {
            if (versionShouldBeUpdated) {
              processRepository.saveProcess(name, GraphProcess(fromFile(file).mkString))
            } else {
              logger.info(s"Process $name not updated. DB version is: \n${latestVersion.flatMap(_.json).getOrElse("")}\n and version from file is: \n$processJson")
              Future.successful(Xor.right(()))
            }
          }.map {
            _ => processRepository.updateCategory(name, category)
          }
        } yield ()
        Await.result(updateProcess, 1 second)
      }
    }
    updateTechnicalProcesses()
  }

  def updateTechnicalProcesses(): Unit = {
    ConfigFactory.parseFile(new File(initialProcessDirectory, "customProcesses.conf")).entrySet().toSet
      .foreach { (entry: Entry[String, ConfigValue]) =>
        val name = entry.getKey
        logger.info(s"Saving custom process $name")
        val savedProces = processRepository
          .saveProcess(name, CustomProcess(entry.getValue.unwrapped().toString))
          .map {
            _ => processRepository.updateCategory(name, "Technical")
          }
        Await.result(savedProces, 1 second)
      }
  }

  def insertEnvironment(environmentName: String) = {
    import DefaultJdbcProfile.profile.api._
    val insertAction = EspTables.environmentsTable += EnvironmentsEntityData(environmentName)
    db.run(insertAction).map(_ => ())
  }


}

object Initialization {

  def init(processRepository: ProcessRepository,
           db: JdbcBackend.DatabaseDef, environment: String, isDevelopmentMode: Boolean, initialProcessDirectory: File) = {
    val initialization = new Initialization(processRepository, db, environment, initialProcessDirectory)
    initialization.insertInitialProcesses()
    initialization.insertEnvironment(environment)
    if (isDevelopmentMode) {
      SampleDataInserter.insert(db)
    }
  }


}
