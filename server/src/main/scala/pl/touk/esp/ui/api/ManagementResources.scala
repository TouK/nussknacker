package pl.touk.esp.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess, ProcessDeploymentData, ProcessManager}
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessRepository}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

class ManagementResources(processRepository: ProcessRepository,
                          deployedProcessRepository: DeployedProcessRepository,
                          processManager: ProcessManager)(implicit ec: ExecutionContext) extends Directives {

  val route =
    path("processManagement" / "deploy" / Segment) { id =>
      post {
        complete {
          processRepository.fetchProcessDeploymentById(id).flatMap {
            case Some(deployment) =>
              deployAndSaveProcess(id, deployment).map { _ =>
                HttpResponse(status = StatusCodes.OK)
              }
            case None => Future(HttpResponse(
              status = StatusCodes.NotFound,
              entity = "Process not found"
            ))
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
            }
          }
        }
      }

  private def deployAndSaveProcess(id: String, deployment: ProcessDeploymentData): Future[Unit] = {
    processManager.deploy(id, deployment).flatMap { _ =>
      deployment match {
        case GraphProcess(json) =>
          deployedProcessRepository.saveDeployedProcess(id, json).recoverWith { case NonFatal(e) =>
            processManager.cancel(id)
          }
        case CustomProcess(_) =>
          Future.successful(Unit)
      }
    }
  }
}
