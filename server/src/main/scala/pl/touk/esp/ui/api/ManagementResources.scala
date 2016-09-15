package pl.touk.esp.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import pl.touk.esp.engine.api.deployment.ProcessManager
import pl.touk.esp.ui.process.repository.ProcessRepository

import scala.concurrent.{ExecutionContext, Future}

class ManagementResources(processRepository: ProcessRepository,
                          processManager: ProcessManager)(implicit ec: ExecutionContext) extends Directives {

  val route =
    path("processManagement" / "deploy" / Segment) { id =>
      post {
        complete {
          processRepository.fetchProcessDeploymentById(id).flatMap {
            case Some(deployment) =>
              processManager.deploy(id, deployment)
                .map(_ => HttpResponse(
                  status = StatusCodes.OK
                ))
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

}
