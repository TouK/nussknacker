package pl.touk.nussknacker.ui.api


import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import cats.data.Validated.{Invalid, Valid}
import cats.instances.future._
import cats.data.{EitherT, Validated}
import cats.syntax.either._
import pl.touk.nussknacker.engine.api.deployment.GraphProcess
import pl.touk.nussknacker.ui.api.ProcessesResources.{UnmarshallError, WrongProcessId}
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ProcessStatus, ValidatedDisplayableProcess}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.{FetchingProcessRepository, ProcessActivityRepository, WriteProcessRepository}
import pl.touk.nussknacker.ui.process.repository.ProcessRepository._
import pl.touk.nussknacker.ui.util._
import pl.touk.nussknacker.ui._
import EspErrorToHttp._
import cats.Monad
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.ui.validation.{FatalValidationError, ProcessValidation}
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent._
import pl.touk.nussknacker.restmodel.process.{ProcessId, ProcessIdWithName}
import pl.touk.nussknacker.restmodel.processdetails.{BaseProcessDetails, BasicProcess, ProcessDetails, ValidatedProcessDetails}
import pl.touk.nussknacker.restmodel.validation.ValidationResults
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.ui.process.repository.WriteProcessRepository.UpdateProcessAction
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.ui.db.entity.ProcessVersionEntityData
import pl.touk.nussknacker.ui.listener.ProcessChangeListener
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent.OnCategoryChanged

class ProcessesResources(val processRepository: FetchingProcessRepository[Future],
                         writeRepository: WriteProcessRepository,
                         jobStatusService: JobStatusService,
                         processActivityRepository: ProcessActivityRepository,
                         processValidation: ProcessValidation,
                         processResolving: UIProcessResolving,
                         typesForCategories: ProcessTypesForCategories,
                         newProcessPreparer: NewProcessPreparer,
                         val processAuthorizer:AuthorizeProcess,
                         processChangeListener: ProcessChangeListener)
                        (implicit val ec: ExecutionContext, mat: Materializer)
  extends Directives
    with FailFastCirceSupport
    with EspPathMatchers
    with RouteWithUser
    with LazyLogging
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  import akka.http.scaladsl.unmarshalling.Unmarshaller._

  def securedRoute(implicit user: LoggedUser): Route = {
      encodeResponse {
        path("archive") {
          get {
            complete {
              processRepository.fetchArchivedProcesses[Unit]().toBasicProcess
            }
          }
        } ~ path("unarchive" / Segment) { processName =>
          (post & processId(processName)) { processId =>
            canWrite(processId) {
              complete {
                writeArchive(processId.id, isArchived = false)
                  .withSideEffect(_ => processChangeListener.handle(OnUnarchived(processId.id)))
              }
            }
          }
        } ~ path("archive" / Segment) { processName =>
          (post & processId(processName)) { processId =>
            canWrite(processId) {
              /*
              should not allow to archive still used subprocess IGNORED TEST
              */
              complete {
                writeArchive(processId.id, isArchived = true)
                  .withSideEffect(_ => processChangeListener.handle(OnArchived(processId.id)))
              }
            }
          }
        }  ~ path("processes") {
          get {
            parameters(
              'isSubprocess.as[Boolean].?,
              'isArchived.as[Boolean].?,
              'isDeployed.as[Boolean].?,
              'categories.as(CsvSeq[String]).?,
              'processingTypes.as(CsvSeq[String]).?
            ) { (isSubprocess, isArchived, isDeployed, categories, processingTypes) =>
              complete {
                processRepository.fetchProcesses[Unit](
                  isSubprocess,
                  isArchived.orElse(Option(false)), //Back compability
                  isDeployed,
                  categories,
                  processingTypes
                ).toBasicProcess
              }
            }
          }
        } ~ path("customProcesses") {
          get {
            complete {
              processRepository.fetchCustomProcesses[Unit]().toBasicProcess
            }
          }
        } ~ path("processesDetails") {
          get {
            parameter('names.as(CsvSeq[String])) { namesToFetch =>
              complete {
                validateAndReverseResolveAll(processRepository.fetchProcessesDetails(namesToFetch.map(ProcessName(_)).toList))
              }
            } ~
            complete {
              validateAndReverseResolveAll(processRepository.fetchProcessesDetails())
            }
          }
        } ~ path("processesComponents" / Segment) { componentId =>
          get {
            complete {
              processRepository.fetchAllProcessesDetails[DisplayableProcess]().map { processList =>
                ProcessObjectsFinder.findComponents(processList, componentId)
              }
            }
          }
        } ~ path("subProcesses") {
          get {
            complete {
              processRepository.fetchSubProcessesDetails[Unit]().toBasicProcess
            }
          }
        } ~ path("subProcessesDetails") {
          get {
            complete {
              validateAndReverseResolveAll(processRepository.fetchSubProcessesDetails[CanonicalProcess]())
            }
          }
        } ~ path("processes" / "status") {
          get {
            complete {
              for {
                processes <- processRepository.fetchProcesses[Unit]()
                customProcesses <- processRepository.fetchCustomProcesses[Unit]()
                statuses <- fetchProcessStatesForProcesses(processes ++ customProcesses)
              } yield statuses
            }
          }
        } ~ path("processes" / "import" / Segment) { processName =>
          processId(processName) { processId =>
            (canWrite(processId) & post) {
              fileUpload("process") { case (_, byteSource) =>
                complete {
                  MultipartUtils.readFile(byteSource).map[ToResponseMarshallable] { json =>
                    validateJsonForImport(processId, json) match {
                      case Valid(process) => importProcess(processId, process)
                      case Invalid(error) => EspErrorToHttp.espErrorToHttp(error)
                    }
                  }
                }
              }
            }
          }
        } ~ path("processes" / Segment / "deployments") { processName =>
          processId(processName) { processId =>
            complete {
              processRepository.fetchDeploymentHistory(processId.id)
            }
          }
        } ~ path("processes" / Segment) { processName =>
          processId(processName) { processId =>
            (delete & canWrite(processId)) {
              complete {
                writeRepository.deleteProcess(processId.id).map(toResponse(StatusCodes.OK))
                  .withSideEffect(_ => processChangeListener.handle(OnDeleted(processId.id)))
              }
            } ~ (put & canWrite(processId)) {
              entity(as[ProcessToSave]) { processToSave =>
                complete {
                  isArchived(processId.id).flatMap[ToResponseMarshallable] {
                    case true =>
                      rejectSavingArchivedProcess
                    case false =>
                      saveProcess(processToSave, processId.id)
                        .withSideEffect(_.toOption.flatMap(_.processVersionEntityData).foreach(p => processChangeListener.handle(OnSaved(processId.id, p.id))))
                        .map(_.map(_.validation))
                        .map(toResponseEither[ValidationResult])
                  }
                }
              }
            } ~ parameter('businessView ? false) { (businessView) =>
              get {
                complete {
                  processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](processId.id, businessView).map[ToResponseMarshallable] {
                    case Some(process) =>
                      // todo: we should really clearly separate backend objects from ones returned to the front
                      validateAndReverseResolve(process, businessView)
                        .map { validatedProcess =>
                          val historyWithoutId = validatedProcess.history.map(_.copy(processId = processName))
                          validatedProcess.copy(id = processName, history = historyWithoutId)
                        }
                    case None => HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
                  }
                }
              }
            }
          }
        } ~ path("processes" / Segment / "rename" / Segment) { (processName, newName) =>
          (put & processId(processName)) { processId =>
            canWrite(processId) {
              complete {
                processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).flatMap {
                  case Some(details) if details.deployment.isEmpty =>
                    writeRepository.renameProcess(processId.id, newName).map(toResponse(StatusCodes.OK))
                      .withSideEffect(_ => processChangeListener.handle(OnRenamed(processId.id, processId.name, ProcessName(newName))))
                  case _ => Future.successful(espErrorToHttp(ProcessAlreadyDeployed(processName)))
                }
              }
            }
          }
        } ~ path("processes" / Segment / LongNumber) { (processName, versionId) =>
          (get & processId(processName)) { processId =>
            parameter('businessView ? false) { businessView =>
              complete {
                processRepository.fetchProcessDetailsForId[CanonicalProcess](processId.id, versionId, businessView).map[ToResponseMarshallable] {
                  case Some(process) =>
                    // todo: we should really clearly separate backend objects from ones returned to the front
                    validateAndReverseResolve(process, businessView)
                      .map { validatedProcess =>
                        val historyWithoutId = validatedProcess.history.map(_.copy(processId = processName))
                        validatedProcess.copy(id = processName, history = historyWithoutId)
                      }
                  case None => HttpResponse(status = StatusCodes.NotFound, entity = "Process not found")
                }
              }
            }
          }
        } ~ path("processes" / Segment / Segment) { (processName, category) =>
          authorize(user.can(category, Permission.Write)) {
            parameter('isSubprocess ? false) { isSubprocess =>
              post {
                complete {
                  typesForCategories.getTypeForCategory(category) match {
                    case Some(processingType) =>
                      val emptyProcess = makeEmptyProcess(processName, processingType, isSubprocess)
                      writeRepository.saveNewProcess(
                        processName = ProcessName(processName),
                        category = category,
                        processDeploymentData = emptyProcess,
                        processingType = processingType,
                        isSubprocess = isSubprocess
                      )
                        .withSideEffect(_.toOption.flatten.foreach(p => processChangeListener.handle(OnSaved(ProcessId(p.processId), p.id))))
                        .map(_.map(_ => ()))
                        .map(toResponse(StatusCodes.Created))
                    case None => Future(HttpResponse(status = StatusCodes.BadRequest, entity = "Process category not found"))
                  }
                }
              }
            }
          }
        } ~ path("processes" / Segment / "status") { processName =>
          (get & processId(processName)) { processId =>
            complete {
              processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId.id).flatMap[ToResponseMarshallable] {
                case Some(process) =>
                  findJobStatus(processId, process.processingType).map {
                    case Some(status) => status
                    case None => ProcessStatus.notFound
                  }
                case None =>
                  Future.successful(HttpResponse(status = StatusCodes.NotFound, entity = "Process not found"))
              }
            }
          }
        } ~ path("processes" / "category" / Segment / Segment) { (processName, category) =>
          (post & processIdWithCategory(processName)) { data =>
            hasAdminPermission(user) {
              complete {
                // TODO: Validate that category exists at categories list
                writeRepository.updateCategory(processId = data.id, category = category).map(toResponse(StatusCodes.OK))
                  .withSideEffect(_ => processChangeListener.handle(OnCategoryChanged(data.id, oldCategory = data.category, newCategory = category)))
              }
            }
          }
        } ~ path("processes" / Segment / LongNumber / "compare" / LongNumber) { (processName, thisVersion, otherVersion) =>
          (get & processId(processName)) { processId =>
            parameter('businessView ? false) { businessView =>
              complete {
                withJson(processId.id, thisVersion, businessView) { thisDisplayable =>
                  withJson(processId.id, otherVersion, businessView) { otherDisplayable =>
                    ProcessComparator.compare(thisDisplayable, otherDisplayable)
                  }
                }
              }
            }
          }
        }
      }
  }

  private def validateJsonForImport(processId: ProcessIdWithName, json: String): Validated[EspError, CanonicalProcess] = {
    ProcessMarshaller.fromJson(json) match {
      case Valid(process) if process.metaData.id != processId.name.value =>
    Invalid(WrongProcessId(processId.name.value, process.metaData.id))
      case Valid(process) => Valid(process)
      case Invalid(unmarshallError) => Invalid(UnmarshallError(unmarshallError.msg))
    }
  }

  private def importProcess(processId: ProcessIdWithName, process: CanonicalProcess)
                           (implicit user: LoggedUser): Future[ToResponseMarshallable] = {
    processRepository.fetchLatestProcessDetailsForProcessIdEither[Unit](processId.id).map { detailsXor =>
      val validatedProcess = detailsXor
        .map(details => ProcessConverter.toDisplayable(process, details.processingType))
        .map(process => new ValidatedDisplayableProcess(process, processValidation.validate(process)))
      toResponseXor(validatedProcess)
    }
  }

  private def writeArchive(processId: ProcessId, isArchived: Boolean) = {
    writeRepository.archive(processId = processId, isArchived = isArchived)
      .map(toResponse(StatusCodes.OK))
  }
  private def isArchived(processId: ProcessId)(implicit loggedUser: LoggedUser): Future[Boolean] =
    processRepository.fetchLatestProcessDetailsForProcessId[Unit](processId)
      .map {
        case Some(details) => details.isArchived
        case _ => false
      }

  case class SaveProcessResult(processVersionEntityData: Option[ProcessVersionEntityData], validation: ValidationResults.ValidationResult)

  private def saveProcess(processToSave: ProcessToSave, processId: ProcessId)
                         (implicit loggedUser: LoggedUser):Future[Either[EspError, SaveProcessResult]] = {
    val displayableProcess = processToSave.process
    (for {
      validation <- EitherT.fromEither[Future](FatalValidationError.saveNotAllowedAsError(processResolving.validateBeforeUiResolving(displayableProcess)))
      deploymentData = {
        val substituted = processResolving.resolveExpressions(displayableProcess, validation.typingInfo)
        val json = ProcessMarshaller.toJson(substituted).noSpaces
        GraphProcess(json)
      }
      updateResult <- EitherT(writeRepository.updateProcess(UpdateProcessAction(processId, deploymentData, processToSave.comment)))
    } yield SaveProcessResult(updateResult, validation.withClearedTypingInfo)).value
  }
  private def rejectSavingArchivedProcess: Future[ToResponseMarshallable]=
    Future.successful(HttpResponse(status = StatusCodes.Forbidden, entity = "Cannot save archived process"))

  private def fetchProcessStatesForProcesses(processes: List[BaseProcessDetails[Unit]])(implicit user: LoggedUser): Future[Map[String, Option[ProcessStatus]]] = {
    import cats.instances.future._
    import cats.instances.list._
    import cats.syntax.traverse._
    processes.map(process => findJobStatus(process.idWithName, process.processingType).map(status => process.name -> status))
      .sequence[Future, (String, Option[ProcessStatus])].map(_.toMap)
  }

  private def findJobStatus(processId: ProcessIdWithName, processingType: ProcessingType)(implicit ec: ExecutionContext, user: LoggedUser): Future[Option[ProcessStatus]] = {
    jobStatusService.retrieveJobStatus(processId).recover {
      case NonFatal(e) =>
        logger.warn(s"Failed to get status of $processId: ${e.getMessage}", e)
        Some(ProcessStatus.failedToGet)
    }
  }

  private def makeEmptyProcess(processId: String, processingType: ProcessingType, isSubprocess: Boolean) = {
    val emptyCanonical = newProcessPreparer.prepareEmptyProcess(processId, processingType, isSubprocess)
    GraphProcess(ProcessMarshaller.toJson(emptyCanonical).noSpaces)
  }

  private def withJson(processId: ProcessId, version: Long, businessView: Boolean)
                      (process: DisplayableProcess => ToResponseMarshallable)(implicit user: LoggedUser): ToResponseMarshallable
  = processRepository.fetchProcessDetailsForId[DisplayableProcess](processId, version, businessView).map { maybeProcess =>
      maybeProcess.flatMap(_.json) match {
        case Some(displayable) => process(displayable)
        case None => HttpResponse(status = StatusCodes.NotFound, entity = s"Process $processId in version $version not found"): ToResponseMarshallable
      }
  }

  private def validateAndReverseResolveAll(processDetails: Future[List[BaseProcessDetails[CanonicalProcess]]]) : Future[List[ValidatedProcessDetails]] = {
    processDetails.flatMap(all => Future.sequence(all.map(validateAndReverseResolve(_, businessView = false))))
  }

  private def validateAndReverseResolve(processDetails: BaseProcessDetails[CanonicalProcess], businessView: Boolean): Future[ValidatedProcessDetails] = {
    val validatedDetails = processDetails.mapProcess { canonical: CanonicalProcess =>
      val processingType = processDetails.processingType
      val validationResult = processResolving.validateBeforeUiReverseResolving(canonical, processingType)
      processResolving.reverseResolveExpressions(canonical, processingType, businessView, validationResult)
    }
    Future.successful(validatedDetails)
  }

  private implicit class ToBasicConverter(self: Future[List[BaseProcessDetails[_]]]) {
    def toBasicProcess: Future[List[BasicProcess]] = self.map(f => f.map(bpd => BasicProcess(bpd)))
  }
}

object ProcessesResources {
  case class UnmarshallError(message: String) extends Exception(message) with FatalError

  case class WrongProcessId(processId: String, givenId: String) extends Exception(s"Process has id $givenId instead of $processId") with BadRequestError

  case class ProcessNotInitializedError(id: String) extends Exception(s"Process $id is not initialized") with NotFoundError

  case class NodeNotFoundError(processId: String, nodeId: String) extends Exception(s"Node $nodeId not found inside process $processId") with NotFoundError

}