package pl.touk.esp.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData, ProcessManager}
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessRepository}
import pl.touk.esp.ui.security.{LoggedUser, Permission}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ManagementResources(processRepository: ProcessRepository,
                          deployedProcessRepository: DeployedProcessRepository,
                          processManager: ProcessManager)(implicit ec: ExecutionContext) extends Directives with LazyLogging {

  val route = (user: LoggedUser) => {
    authorize(user.hasPermission(Permission.Deploy)) {
      path("processManagement" / "deploy" / Segment) { id =>
        post {
          complete {
            processRepository.fetchLatestProcessDeploymentForId(id).flatMap {
              case Some(deployment) =>
                deployAndSaveProcess(id, deployment).map { _ =>
                  HttpResponse(status = StatusCodes.OK)
                }
              case None => Future(HttpResponse(
                status = StatusCodes.NotFound,
                entity = "Process not found"
              ))
            }.recover { case error =>
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


  private def deployAndSaveProcess(processName: String, deployment: ProcessDeploymentData): Future[Unit] = {
    logger.debug(s"Deploy of $processName started")
    processManager.deploy(processName, deployment).flatMap { _ =>
      deployment match {
        case GraphProcess(json) =>
          logger.debug(s"Deploy of $processName finished")
          deployedProcessRepository.saveDeployedProcess(processName, json).recoverWith { case NonFatal(e) =>
            processManager.cancel(processName)
          }
        case CustomProcess(_) =>
          logger.debug(s"Deploy of $processName finished")
          Future.successful(Unit)
      }
    }
  }
}
