package pl.touk.esp.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server.Directives
import pl.touk.esp.engine.management.ProcessManager
import pl.touk.esp.ui.process.repository.ProcessRepository

import scala.concurrent.{ExecutionContext, Future}

class ManagementResources(processRepository: ProcessRepository,
                          processManager: ProcessManager)(implicit ec: ExecutionContext) extends Directives {

  val route =
    path("processManagement" / "deploy" / Segment) { id =>
      post {
        complete {
          processRepository.fetchProcessJsonById(id).flatMap {
            case Some(json) =>
              processManager.deploy(id, json)
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
    }

}
