package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model._
import akka.http.scaladsl.server._
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.ProcessingTypeData
import pl.touk.nussknacker.engine.api.component.SingleComponentConfig
import pl.touk.nussknacker.engine.api.deployment.{DeploymentManager, ProcessState}
import pl.touk.nussknacker.engine.api.process.{ProcessId, ProcessName, VersionId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.component.ComponentsUiConfigExtractor
import pl.touk.nussknacker.engine.definition.SubprocessDefinitionExtractor
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.restmodel.displayedgraph.{DisplayableProcess, ValidatedDisplayableProcess}
import pl.touk.nussknacker.restmodel.process._
import pl.touk.nussknacker.restmodel.processdetails._
import pl.touk.nussknacker.restmodel.validation.ValidationResults.ValidationResult
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui.EspError.XError
import pl.touk.nussknacker.ui._
import pl.touk.nussknacker.ui.api.EspErrorToHttp._
import pl.touk.nussknacker.ui.api.ProcessesResources.ProcessesQuery
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent._
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.process.ProcessService.{CreateProcessCommand, UpdateProcessCommand}
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository.FetchProcessesDetailsQuery
import pl.touk.nussknacker.ui.process.subprocess.SubprocessRepository
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.uiresolving.UIProcessResolving
import pl.touk.nussknacker.ui.util._

import scala.concurrent.{ExecutionContext, Future}

//TODO: Move remained business logic to processService
class ProcessesResources(
                          val processRepository: FetchingProcessRepository[Future],
                          subprocessRepository: SubprocessRepository,
                          processService: ProcessService,
                          processToolbarService: ProcessToolbarService,
                          processResolving: UIProcessResolving,
                          val processAuthorizer:AuthorizeProcess,
                          processChangeListener: ProcessChangeListener,
                          typeToConfig: ProcessingTypeDataProvider[ProcessingTypeData]
)(implicit val ec: ExecutionContext, mat: Materializer)
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
              processService.getArchivedProcesses[Unit](user).toBasicProcess
            }
          }
        } ~ path("unarchive" / Segment) { processName =>
          (post & processId(processName)) { processId =>
            canWrite(processId) {
              complete {
                processService.unArchiveProcess(processId)
                  .map(toResponse(StatusCodes.OK))
                  .withSideEffect(_ => sideEffectAction(OnUnarchived(processId.id)))
              }
            }
          }
        } ~ path("archive" / Segment) { processName =>
          (post & processId(processName)) { processId =>
            canWrite(processId) {
              complete {
                processService.archiveProcess(processId)
                  .map(toResponse(StatusCodes.OK))
                  .withSideEffect(_ => sideEffectAction(OnArchived(processId.id)))
              }
            }
          }
        }  ~ path("processes") {
          get {
            processesQuery { query =>
              complete {
                processRepository.fetchProcessesDetails[Unit](query.toRepositoryQuery)
                  .map(_.map(enrichDetailsWithProcessState[Unit])).toBasicProcess //TODO: Remove enrichProcess when we will support cache for state
              }
            }
          }
        } ~ path("processesDetails") {
          (get & processesQuery & skipValidateAndResolveParameter) { (query, skipValidateAndResolve) =>
            complete {
              val processes = processRepository.fetchProcessesDetails[CanonicalProcess](query.toRepositoryQuery)
              if (skipValidateAndResolve) {
                toProcessDetailsAll(processes)
              } else {
                validateAndReverseResolveAll(processes)
              }
            }
          }
        } ~ path("processes" / "status") {
          get {
            complete {
              for {
                processes <- processService.getProcesses[Unit](user)
                statuses <- fetchProcessStatesForProcesses(processes)
              } yield statuses
            }
          }
        } ~ path("processes" / "import" / Segment) { processName =>
          processId(processName) { processId =>
            (canWrite(processId) & post) {
              fileUpload("process") { case (_, byteSource) =>
                complete {
                  MultipartUtils
                    .readFile(byteSource)
                    .flatMap(processService.importProcess(processId, _))
                    .map(toResponseEither[ValidatedDisplayableProcess])
                }
              }
            }
          }
        } ~ path("processes" / Segment / "deployments") { processName =>
          processId(processName) { processId =>
            complete {
              //FIXME: We should provide Deployment definition and return there all deployments, not actions..
              processRepository.fetchProcessActions(processId.id)
            }
          }
        } ~ path("processes" / Segment) { processName =>
          processId(processName) { processId =>
            (delete & canWrite(processId)) {
              complete {
                processService
                  .deleteProcess(processId)
                  .map(toResponse(StatusCodes.OK))
                  .withSideEffect(_ => sideEffectAction(OnDeleted(processId.id)))
              }
            } ~ (put & canWrite(processId)) {
              entity(as[UpdateProcessCommand]) { updateCommand =>
                complete {
                  processService
                    .updateProcess(processId, updateCommand)
                    .withSideEffect(response => sideEffectAction(response.toOption.flatMap(_.processResponse)) { resp =>
                      OnSaved(resp.id, resp.versionId)
                    })
                    .map(_.map(_.validationResult))
                    .map(toResponseEither[ValidationResult])
                }
              }
            } ~ (get & skipValidateAndResolveParameter) { skipValidateAndResolve =>
              complete {
                processRepository.fetchLatestProcessDetailsForProcessId[CanonicalProcess](processId.id).map[ToResponseMarshallable] {
                  case Some(process) if skipValidateAndResolve => toProcessDetails(enrichDetailsWithProcessState(process))
                  case Some(process) => {
                    val processTypeData  = typeToConfig.forType(process.processingType)
                    val processConfig    = processTypeData.map(_.modelData).map(_.processConfig).get
                    val classLoader = processTypeData.map(_.modelData).map(_.modelClassLoader).map(_.classLoader).get
                    val subprocessesConfig:Map[String,SingleComponentConfig] = ComponentsUiConfigExtractor.extract(processConfig)
                    val subprocessesDetails = subprocessRepository.loadSubprocesses(Map.empty, process.processCategory)
                    val subprocessDefinitionExtractor = SubprocessDefinitionExtractor.apply(
                      category = process.processCategory,
                      subprocessesDetails = subprocessesDetails.map { d => pl.touk.nussknacker.engine.definition.SubprocessDetails(d.canonical, d.category)},
                      subprocessesConfig  = subprocessesConfig,
                      classLoader         = classLoader
                    )
                    val subprocessesDefinition = subprocessDefinitionExtractor.extractBySubprocessId(process.id)

                    validateAndReverseResolve(enrichDetailsWithProcessState(process))
                  }
                  case None => HttpResponse(status = StatusCodes.NotFound, entity = "Scenario not found")
                }
              }
            }
          }
        } ~ path("processes" / Segment / "rename" / Segment) { (processName, newName) =>
          (put & processId(processName)) { processId =>
            canWrite(processId) {
              complete {
                processService
                  .renameProcess(processId, ProcessName(newName))
                  .withSideEffect(response => sideEffectAction(response) { resp =>
                    OnRenamed(processId.id, resp.oldName, resp.newName)
                  })
                  .map(toResponseEither[UpdateProcessNameResponse])
              }
            }
          }
        } ~ path("processes" / Segment / VersionIdSegment) { (processName, versionId) =>
          (get & processId(processName) & skipValidateAndResolveParameter) { (processId, skipValidateAndResolve) =>
            complete {
              processRepository.fetchProcessDetailsForId[CanonicalProcess](processId.id, versionId).map[ToResponseMarshallable] {
                case Some(process) if skipValidateAndResolve => toProcessDetails(enrichDetailsWithProcessState(process))
                case Some(process) => validateAndReverseResolve(enrichDetailsWithProcessState(process))
                case None => HttpResponse(status = StatusCodes.NotFound, entity = "Scenario not found")
              }
            }
          }
        } ~ path("processes" / Segment / Segment) { (processName, category) =>
          authorize(user.can(category, Permission.Write)) {
            parameter(Symbol("isSubprocess") ? false) { isSubprocess =>
              post {
                complete {
                  processService
                    .createProcess(CreateProcessCommand(ProcessName(processName), category, isSubprocess))
                    .withSideEffect(response => sideEffectAction(response) { process =>
                      OnSaved(process.id, process.versionId)
                    })
                    .map(toResponseEither[ProcessResponse](_, StatusCodes.Created))
                }
              }
            }
          }
        } ~ path("processes" / Segment / "status") { processName =>
          (get & processId(processName)) { processId =>
            complete {
              processService.getProcessState(processId).map(ToResponseMarshallable(_))
            }
          }
        } ~ path("processes" / Segment / "toolbars") { processName =>
          (get & processId(processName)) { processId =>
            complete {
              processService
                .getProcess[Unit](processId)
                .map(resp => resp.map(processToolbarService.getProcessToolbarSettings))
                .map(toResponseEither[ProcessToolbarSettings])
            }
          }
        } ~ path("processes" / "category" / Segment / Segment) { (processName, category) =>
          (post & processId(processName)) { processId =>
            hasAdminPermission(user) {
              complete {
                processService
                  .updateCategory(processId, category)
                  .withSideEffect(response => sideEffectAction(response) { resp =>
                    OnCategoryChanged(processId.id, resp.oldCategory, resp.newCategory)
                  })
                  .map(toResponseEither[UpdateProcessCategoryResponse])
              }
            }
          }
        } ~ path("processes" / Segment / VersionIdSegment / "compare" / VersionIdSegment) { (processName, thisVersion, otherVersion) =>
          (get & processId(processName)) { processId =>
            complete {
              withJson(processId.id, thisVersion) { thisDisplayable =>
                withJson(processId.id, otherVersion) { otherDisplayable =>
                  ProcessComparator.compare(thisDisplayable, otherDisplayable)
                }
              }
            }
          }
        }

      }
  }

  private def sideEffectAction(event: ProcessChangeEvent)(implicit user: LoggedUser): Unit = {
    implicit val listenerUser: User = ListenerApiUser(user)
    processChangeListener.handle(event)
  }

  private def sideEffectAction[T](response: XError[T])(eventAction: T => ProcessChangeEvent)(implicit user: LoggedUser): Unit = {
    sideEffectAction(response.toOption)(eventAction)
  }

  private def sideEffectAction[T](response: Option[T])(eventAction: T => ProcessChangeEvent)(implicit user: LoggedUser): Unit = {
    implicit val listenerUser: User = ListenerApiUser(user)
    response.foreach(resp => processChangeListener.handle(eventAction(resp)))
  }

  private def fetchProcessStatesForProcesses(processes: List[BaseProcessDetails[Unit]])(implicit user: LoggedUser): Future[Map[String, ProcessState]] = {
    import cats.instances.future._
    import cats.instances.list._
    import cats.syntax.traverse._
    processes.map(process => processService.getProcessState(process.idWithName).map(status => process.name -> status))
      .sequence[Future, (String, ProcessState)].map(_.toMap)
  }

  //This is temporary function to enriching process state data
  //TODO: Remove it when we will support cache for state
  private def enrichDetailsWithProcessState[PS: ProcessShapeFetchStrategy](process: BaseProcessDetails[PS]): BaseProcessDetails[PS] =
    process.copy(state = deploymentManager(process.processingType).map(m => m.processStateDefinitionManager.processState(
      m.processStateDefinitionManager.mapActionToStatus(process.lastAction.map(_.action))
    )))

  private def deploymentManager(processingType: ProcessingType): Option[DeploymentManager] =
    typeToConfig.forType(processingType).map(_.deploymentManager)

  private def withJson(processId: ProcessId, version: VersionId)
                      (process: DisplayableProcess => ToResponseMarshallable)(implicit user: LoggedUser): ToResponseMarshallable
  = processRepository.fetchProcessDetailsForId[DisplayableProcess](processId, version).map { maybeProcess =>
      maybeProcess.map(_.json) match {
        case Some(displayable) => process(displayable)
        case None => HttpResponse(status = StatusCodes.NotFound, entity = s"Scenario $processId in version $version not found"): ToResponseMarshallable
      }
  }

  private def validateAndReverseResolveAll(processDetails: Future[List[BaseProcessDetails[CanonicalProcess]]]) : Future[List[ValidatedProcessDetails]] = {
    processDetails.flatMap(all => Future.sequence(all.map(validateAndReverseResolve)))
  }

  private def validateAndReverseResolve(processDetails: BaseProcessDetails[CanonicalProcess]): Future[ValidatedProcessDetails] = {
    val validatedDetails = processDetails.mapProcess { canonical: CanonicalProcess =>
      val processingType = processDetails.processingType
      val validationResult = processResolving.validateBeforeUiReverseResolving(canonical, processingType, processDetails.processCategory)
      processResolving.reverseResolveExpressions(canonical, processingType, processDetails.processCategory, validationResult)
    }
    Future.successful(validatedDetails)
  }

  private def toProcessDetails(canonicalProcessDetails: BaseProcessDetails[CanonicalProcess]): Future[ProcessDetails] = {
    val processDetails = canonicalProcessDetails.mapProcess(canonical => ProcessConverter.toDisplayable(canonical, canonicalProcessDetails.processingType, canonicalProcessDetails.processCategory))
    Future.successful(processDetails)
  }

  private def toProcessDetailsAll(canonicalProcessDetails: Future[List[BaseProcessDetails[CanonicalProcess]]]): Future[List[ProcessDetails]] = {
    canonicalProcessDetails.flatMap(all => Future.sequence(all.map(toProcessDetails)))
  }

  private implicit class ToBasicConverter(self: Future[List[BaseProcessDetails[_]]]) {
    def toBasicProcess: Future[List[BasicProcess]] = self.map(f => f.map(bpd => BasicProcess(bpd)))
  }

  private def processesQuery: Directive1[ProcessesQuery] = {
    parameters(
      Symbol("isSubprocess").as[Boolean].?,
      Symbol("isArchived").as[Boolean].?,
      Symbol("isDeployed").as[Boolean].?,
      Symbol("categories").as(CsvSeq[String]).?,
      Symbol("processingTypes").as(CsvSeq[String]).?,
      Symbol("names").as(CsvSeq[String]).?,
    ).as(ProcessesQuery.apply _)
  }

  private def skipValidateAndResolveParameter = {
    parameters(Symbol("skipValidateAndResolve").as[Boolean].withDefault(false))
  }
}

object ProcessesResources {
  case class UnmarshallError(message: String) extends Exception(message) with FatalError

  case class WrongProcessId(processId: String, givenId: String) extends Exception(s"Scenario has id $givenId instead of $processId") with BadRequestError

  case class ProcessNotInitializedError(id: String) extends Exception(s"Scenario $id is not initialized") with NotFoundError

  case class NodeNotFoundError(processId: String, nodeId: String) extends Exception(s"Node $nodeId not found inside scenario $processId") with NotFoundError

  case class ProcessesQuery(isSubprocess: Option[Boolean],
                            isArchived: Option[Boolean],
                            isDeployed: Option[Boolean],
                            categories: Option[Seq[String]],
                            processingTypes: Option[Seq[String]],
                            names: Option[Seq[String]],
                           ) {
    def toRepositoryQuery: FetchProcessesDetailsQuery = FetchProcessesDetailsQuery(
      isSubprocess = isSubprocess,
      isArchived = isArchived,
      isDeployed = isDeployed,
      categories = categories,
      processingTypes = processingTypes,
      names = names.map(_.map(ProcessName(_))),
    )
  }

  object ProcessesQuery {
    def empty: ProcessesQuery = ProcessesQuery(None, None, None, None, None, None)
  }
}
