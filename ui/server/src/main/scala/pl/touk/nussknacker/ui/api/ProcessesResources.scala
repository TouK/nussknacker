package pl.touk.nussknacker.ui.api


import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.{Directive, Directive0, Directives, Route}
import akka.stream.Materializer
import argonaut._
import cats.data.Validated.{Invalid, Valid}
import cats.instances.future._
import cats.data.EitherT
import cats.syntax.either._
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.ui.api.ProcessesResources.{UnmarshallError, WrongProcessId}
import pl.touk.nussknacker.ui.process.displayedgraph.{DisplayableProcess, ProcessStatus, ValidatedDisplayableProcess}
import pl.touk.nussknacker.ui.process.marshall.{ProcessConverter, UiProcessMarshaller}
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActivityRepository, WriteProcessRepository}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository._
import pl.touk.nussknacker.ui.util._
import pl.touk.nussknacker.ui._
import EspErrorToHttp._
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.ui.codec.UiCodecs
import pl.touk.nussknacker.ui.validation.{ProcessValidation, ValidationResults}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.process.{JobStatusService, NewProcessPreparer, ProcessToSave, ProcessTypesForCategories}
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.graph.node.{NodeData, SubprocessInput}
import pl.touk.nussknacker.engine.graph.subprocess.SubprocessRef
import pl.touk.nussknacker.ui.process.repository.WriteProcessRepository.UpdateProcessAction
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}

import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.control.NonFatal
import pl.touk.nussknacker.ui.security.api.PermissionSyntax._

import scala.concurrent.duration._
class ProcessesResources(repository: FetchingProcessRepository,
                         writeRepository: WriteProcessRepository,
                         jobStatusService: JobStatusService,
                         processActivityRepository: ProcessActivityRepository,
                         processValidation: ProcessValidation,
                         typesForCategories: ProcessTypesForCategories,
                         newProcessPreparer: NewProcessPreparer,
                         val processAuthorizer:AuthorizeProcess)
                        (implicit ec: ExecutionContext, mat: Materializer)
  extends Directives
    with Argonaut62Support
    with EspPathMatchers
    with RouteWithUser
    with LazyLogging
    with AuthorizeProcessDirectives {

  import UiCodecs._
  def route(implicit user: LoggedUser): Route = {
      encodeResponse {
        path("archive") {
          get {
              complete {
                repository.fetchArchivedProcesses()
              }
          }
        } ~ path("unarchive" / Segment) { processId =>
          (canWrite(processId) & post) {
            complete(writeArchive(processId, isArchived = false))
          }
        } ~ path("archive" / Segment) { processId =>
          (canWrite(processId) & post)  {
            /*
            should not allow to archive still used subprocess IGNORED TEST
             */
           complete(writeArchive(processId, isArchived = true))
          }
        }  ~ path("processes") {
          get {
            complete {
              repository.fetchProcesses()
            }
          }
        }  ~ path("customProcesses") {
          get {
            complete {
              repository.fetchCustomProcesses()
            }
          }
        } ~ path("processesDetails") {
          get {
            complete {
              validateAll(repository.fetchProcessesDetails())
            }
          }
        } ~ path("subProcesses") {
          get {
            complete {
              repository.fetchSubProcesses()
            }
          }
        } ~ path("subProcessesDetails") {
          get {
            complete {
              validateAll(repository.fetchSubProcessesDetails())
            }
          }
        } ~ path("processes" / "status") {
          get {
            complete {
              for {
                processes <- repository.fetchProcesses()
                customProcesses <- repository.fetchCustomProcesses()
                statuses <- fetchProcessStatesForProcesses(processes ++ customProcesses)
              } yield statuses
            }
          }

        } ~ path("processes" / Segment) { processId =>
          (delete & canWrite(processId))  {
            complete {
              writeRepository.deleteProcess(processId).map(toResponse(StatusCodes.OK))
            }
          } ~ (put & canWrite(processId))  {
            entity(as[ProcessToSave]) { processToSave =>
              complete {
                isArchived(processId).flatMap[ToResponseMarshallable]{
                  case true =>
                    rejectSavingArchivedProcess
                  case false =>
                    saveProcess(processToSave, processId).map(toResponse)
                }
              }
            }
          } ~ parameter('businessView ? false) { (businessView) =>
            get {
              complete {
                repository.fetchLatestProcessDetailsForProcessId(processId, businessView).map[ToResponseMarshallable] {
                  case Some(process) => validate(process, businessView)
                  case None => HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
                }
              }
            }
          }
        } ~ path("processes" / Segment / LongNumber) { (processId, versionId) =>
          parameter('businessView ? false) { (businessView) =>
            get {
              complete {
                repository.fetchProcessDetailsForId(processId, versionId, businessView).map[ToResponseMarshallable] {
                  case Some(process) => validate(process, businessView)
                  case None => HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
                }
              }
            }
          }
        } ~ path("processes" / Segment / Segment) { (processId, category) =>
          authorize(user.can(category, Permission.Write)) {
            parameter('isSubprocess ? false) { (isSubprocess) =>
              post  {
                complete {
                  typesForCategories.getTypeForCategory(category) match {
                    case Some(processingType) =>
                      val emptyProcess = makeEmptyProcess(processId, processingType, isSubprocess)
                      writeRepository.saveNewProcess(
                        processId = processId,
                        category = category,
                        processDeploymentData = emptyProcess,
                        processingType = processingType,
                        isSubprocess = isSubprocess
                      )
                        .map(toResponse(StatusCodes.Created))
                    case None => Future(HttpResponse(status = StatusCodes.BadRequest, entity = "Process category not found"))
                  }
                }
              }
            }
          }
        } ~ path("processes" / Segment / "status") { processId =>
          get {
            complete {
              repository.fetchLatestProcessDetailsForProcessId(processId).flatMap[ToResponseMarshallable] {
                case Some(process) =>
                  findJobStatus(process.name, process.processingType).map {
                    case Some(status) => status
                    case None => HttpResponse(status = StatusCodes.OK, entity = "Process is not running")
                  }
                case None =>
                  Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
              }
            }
          }
        } ~ path("processes" / "category" / Segment / Segment) { (processId, category) =>
          (canWrite(processId) & post)  {
            complete {
              writeRepository.updateCategory(processId = processId, category = category).map(toResponse(StatusCodes.OK))
            }
          }
        } ~ path("processes" / Segment / LongNumber / "compare" / LongNumber) { (processId, thisVersion, otherVersion) =>
          parameter('businessView ? false) { (businessView) =>
            get {
              complete {
                withJson(processId, thisVersion, businessView) { thisDisplayable =>
                  withJson(processId, otherVersion, businessView) { otherDisplayable =>
                    implicit val codec = ProcessComparator.codec
                    ProcessComparator.compare(thisDisplayable, otherDisplayable)
                  }
                }
              }
            }
          }
        } ~ path("processes" / "import" / Segment) { processId =>
          (canWrite(processId) & post)  {
            fileUpload("process") { case (metadata, byteSource) =>
              complete {
                MultipartUtils.readFile(byteSource).map[ToResponseMarshallable] { json =>
                  (UiProcessMarshaller.fromJson(json) match {
                    case Valid(process) if process.metaData.id != processId => Invalid(WrongProcessId(processId, process.metaData.id))
                    case Valid(process) => Valid(process)
                    case Invalid(unmarshallError) => Invalid(UnmarshallError(unmarshallError.msg))
                  }) match {
                    case Valid(process) =>
                      repository.fetchLatestProcessDetailsForProcessIdEither(processId).map { detailsXor =>
                        val validatedProcess = detailsXor.map(details =>
                          ProcessConverter.toDisplayable(process, details.processingType).validated(processValidation)
                        )
                        toResponseXor(validatedProcess)
                      }

                    case Invalid(error) => espErrorToHttp(error)
                  }
                }
              }
            }
        }
      }
    }
  }
  private def writeArchive(processId:String,isArchived:Boolean) = {
    writeRepository.archive(processId = processId, isArchived = isArchived)
      .map(toResponse(StatusCodes.OK))
  }
  private def  isArchived(processId: String)(implicit loggedUser: LoggedUser): Future[Boolean] =
    repository.fetchLatestProcessDetailsForProcessId(processId)
      .map {
        case Some(details) => details.isArchived
        case _ => false
      }
  private def saveProcess(processToSave: ProcessToSave, processId: String)
                         (implicit loggedUser: LoggedUser):Future[Either[EspError, ValidationResults.ValidationResult]] = {
    val displayableProcess = processToSave.process
    val canonical = ProcessConverter.fromDisplayable(displayableProcess)
    val json = UiProcessMarshaller.toJson(canonical, PrettyParams.nospace)
    val deploymentData = GraphProcess(json)

    (for {
      validation <- EitherT.fromEither[Future](processValidation.validate(displayableProcess).saveNotAllowedAsError)
      result <- EitherT(writeRepository.updateProcess(UpdateProcessAction(processId, deploymentData, processToSave.comment)))
    } yield validation).value
  }
  private def rejectSavingArchivedProcess: Future[ToResponseMarshallable]=
    Future.successful(HttpResponse(status = StatusCodes.Forbidden, entity = "Cannot save archived process"))

  private def fetchProcessStatesForProcesses(processes: List[BasicProcess])(implicit user: LoggedUser): Future[Map[String, Option[ProcessStatus]]] = {
    import cats.instances.future._
    import cats.instances.list._
    import cats.syntax.traverse._
    processes.map(process => findJobStatus(process.name, process.processingType).map(status => process.name -> status)).sequence.map(_.toMap)
  }

  private def findJobStatus(processName: String, processingType: ProcessingType)(implicit ec: ExecutionContext, user: LoggedUser): Future[Option[ProcessStatus]] = {
    jobStatusService.retrieveJobStatus(processName).recover {
      case NonFatal(e) =>
        logger.warn(s"Failed to get status of $processName: ${e.getMessage}", e)
        Some(ProcessStatus.failedToGet)
    }
  }

  private def makeEmptyProcess(processId: String, processingType: ProcessingType, isSubprocess: Boolean) = {
    val emptyCanonical = newProcessPreparer.prepareEmptyProcess(processId, processingType, isSubprocess)
    GraphProcess(UiProcessMarshaller.toJson(emptyCanonical, PrettyParams.nospace))
  }

  private def withJson(processId: String, version: Long, businessView: Boolean)
                      (process: DisplayableProcess => ToResponseMarshallable)(implicit user: LoggedUser): ToResponseMarshallable
  = repository.fetchProcessDetailsForId(processId, version, businessView).map { maybeProcess =>
      maybeProcess.flatMap(_.json) match {
        case Some(displayable) => process(displayable)
        case None => HttpResponse(status = StatusCodes.NotFound, entity = s"Process $processId in version $version not found"): ToResponseMarshallable
      }
  }

  private def validate(processDetails: ProcessDetails, businessView: Boolean): Future[ValidatedProcessDetails] = {
    if (businessView) Future.successful(processDetails.mapProcess(_.withSuccessValidation())) else validate(processDetails)
  }

  private def validate(processDetails: ProcessDetails) : Future[ValidatedProcessDetails] = {
    Future.successful(processDetails.mapProcess(_.validated(processValidation)))
  }

  private def validateAll(processDetails: Future[List[ProcessDetails]]) : Future[List[ValidatedProcessDetails]] = {
    processDetails.flatMap(all => Future.sequence(all.map(validate)))
  }

}

object ProcessesResources {
  case class UnmarshallError(message: String) extends Exception(message) with FatalError

  case class WrongProcessId(processId: String, givenId: String) extends Exception(s"Process has id $givenId instead of $processId") with BadRequestError

  case class ProcessNotInitializedError(id: String) extends Exception(s"Process $id is not initialized") with NotFoundError

  case class NodeNotFoundError(processId: String, nodeId: String) extends Exception(s"Node $nodeId not found inside process $processId") with NotFoundError

}