package pl.touk.esp.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.esp.engine.api.deployment.{CustomProcess, GraphProcess, ProcessManager}
import pl.touk.esp.ui.db.entity.ProcessVersionEntity.ProcessVersionEntityData
import pl.touk.esp.ui.process.repository.{DeployedProcessRepository, ProcessRepository}
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import pl.touk.esp.ui.util.MultipartUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import argonaut.Argonaut._
import argonaut.PrettyParams
import pl.touk.esp.engine.definition.ProcessDefinitionProvider
import pl.touk.esp.ui.codec.UiCodecs


class ManagementResources(processRepository: ProcessRepository,
                          deployedProcessRepository: DeployedProcessRepository,
                          processManager: ProcessManager with ProcessDefinitionProvider,
                          environment: String)(implicit ec: ExecutionContext, mat: Materializer) extends Directives with LazyLogging {

  val codecs = UiCodecs.ContextCodecs(processManager.getProcessDefinition.typesInformation)

  import codecs._

  def route(implicit user: LoggedUser): Route = {
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
        } ~
        path("processManagement" / "test" / Segment) { processId =>
          post {
            fileUpload("testData") { case (metadata, byteSource) =>
              complete {
                MultipartUtils.readFile(byteSource).map[ToResponseMarshallable] { testData =>
                  processRepository.fetchLatestProcessVersion(processId).flatMap {
                    case Some(latestVersion) =>

                      processManager.test(processId, latestVersion.deploymentData, TestData(testData.split("\n").toList)).map { results =>
                        HttpResponse(status = StatusCodes.OK, entity =
                          HttpEntity(ContentTypes.`application/json`, results.asJson.pretty(PrettyParams.spaces2)))
                      }
                    case None => Future(HttpResponse(
                      status = StatusCodes.NotFound,
                      entity = "Process not found"
                    ))

                  }
                }
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
