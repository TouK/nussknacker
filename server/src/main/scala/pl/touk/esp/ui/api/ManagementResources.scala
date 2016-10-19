package pl.touk.esp.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess, ProcessManager}
import pl.touk.esp.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessRepository}
import pl.touk.esp.ui.security.{LoggedUser, Permission}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ManagementResources(processRepository: ProcessRepository,
                          deployedProcessRepository: DeployedProcessRepository,
                          processManager: ProcessManager,
                          environment: String)(implicit ec: ExecutionContext) extends Directives with LazyLogging {

  val route = (user: LoggedUser) => {
    authorize(user.hasPermission(Permission.Deploy)) {
      path("processManagement" / "deploy" / Segment) { processId =>
        post {
          complete {
            processRepository.fetchLatestProcessVersion(processId).flatMap {
              case Some(latestVersion) =>
                deployAndSaveProcess(latestVersion, user.id, environment).map { _ =>
                  HttpResponse(status = StatusCodes.OK)
                }
              case None => Future(HttpResponse(
                status = StatusCodes.NotFound,
                entity = "Process not found"
              ))
            }.recover { case error =>
              logger.warn("Failed to deploy", error)
              HttpResponse(status = StatusCodes.InternalServerError, entity = error.getMessage)
            }
          }
        }
      } ~
        path("processManagement" / "cancel" / Segment) { id =>
          post {
            complete {
              processManager.cancel(id).map { _ =>
                HttpResponse(
                  status = StatusCodes.OK
                )
              }.recover { case error =>
                HttpResponse(status = StatusCodes.InternalServerError, entity = error.getMessage)
              }
            }
          }
        }
    }
  }

  private def deployAndSaveProcess(latestVersion: ProcessVersionEntityData, userId: String, environment: String): Future[Unit] = {
    val processId = latestVersion.processId
    logger.debug(s"Deploy of $processId started")
    val deployment = latestVersion.deploymentData
    processManager.deploy(processId, deployment).flatMap { _ =>
      deployment match {
        case GraphProcess(_) =>
          logger.debug(s"Deploy of $processId finished")
          deployedProcessRepository.markProcessAsDeployed(latestVersion, userId, environment).recoverWith { case NonFatal(e) =>
            logger.error("Error during marking process as deployed", e)
            processManager.cancel(processId)
          }
        case CustomProcess(_) =>
          logger.debug(s"Deploy of $processId finished")
          Future.successful(Unit)
      }
    }
  }
}
