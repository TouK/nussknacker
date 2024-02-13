package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.stream.Materializer
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.syntax.EncoderOps
import pl.touk.nussknacker.engine.api.deployment.DataFreshnessPolicy
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.security.Permission
import pl.touk.nussknacker.ui._
import pl.touk.nussknacker.ui.listener.ProcessChangeEvent._
import pl.touk.nussknacker.ui.listener.{ProcessChangeEvent, ProcessChangeListener, User}
import pl.touk.nussknacker.ui.process.ProcessService.{
  CreateScenarioCommand,
  FetchScenarioGraph,
  GetScenarioWithDetailsOptions,
  UpdateScenarioCommand
}
import pl.touk.nussknacker.ui.process._
import pl.touk.nussknacker.ui.process.deployment.DeploymentService
import pl.touk.nussknacker.ui.process.ScenarioQuery
import pl.touk.nussknacker.ui.process.repository.ProcessRepository.RemoteUserName
import pl.touk.nussknacker.ui.security.api.LoggedUser
import pl.touk.nussknacker.ui.util._

import scala.concurrent.{ExecutionContext, Future}
import ScenarioWithDetailsConversions._

class ProcessesResources(
    protected val processService: ProcessService,
    deploymentService: DeploymentService,
    processToolbarService: ScenarioToolbarService,
    val processAuthorizer: AuthorizeProcess,
    processChangeListener: ProcessChangeListener
)(implicit val ec: ExecutionContext, mat: Materializer)
    extends Directives
    with FailFastCirceSupport
    with NuPathMatchers
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
            processService
              .getLatestProcessesWithDetails(
                ScenarioQuery(isArchived = Some(true)),
                GetScenarioWithDetailsOptions.detailsOnly
              )
          }
        }
      } ~ path("unarchive" / ProcessNameSegment) { processName =>
        (post & processId(processName)) { processId =>
          canWrite(processId) {
            complete {
              processService
                .unArchiveProcess(processId)
                .withListenerNotifySideEffect(_ => OnUnarchived(processId.id))
            }
          }
        }
      } ~ path("archive" / ProcessNameSegment) { processName =>
        (post & processId(processName)) { processId =>
          canWrite(processId) {
            complete {
              processService
                .archiveProcess(processId)
                .withListenerNotifySideEffect(_ => OnArchived(processId.id))
            }
          }
        }
      } ~ path("processes") {
        get {
          processesQuery { query =>
            complete {
              processService.getLatestProcessesWithDetails(
                query,
                GetScenarioWithDetailsOptions.detailsOnly.withFetchState
              )
            }
          }
        }
      } ~ path("processesDetails") {
        (get & processesQuery & skipValidateAndResolveParameter & skipNodeResultsParameter) {
          (query, skipValidateAndResolve, skipNodeResults) =>
            complete {
              processService.getLatestProcessesWithDetails(
                query,
                GetScenarioWithDetailsOptions(
                  FetchScenarioGraph(validationFlagsToMode(skipValidateAndResolve, skipNodeResults)),
                  fetchState = false
                )
              )
            }
        }
      } ~ path("processes" / "status") {
        get {
          complete {
            processService
              .getLatestProcessesWithDetails(
                ScenarioQuery(isFragment = Some(false), isArchived = Some(false)),
                GetScenarioWithDetailsOptions.detailsOnly.copy(fetchState = true)
              )
              .map(_.flatMap(details => details.state.map(details.name -> _)).toMap)
          }
        }
      } ~ path("processes" / "import" / ProcessNameSegment) { processName =>
        processId(processName) { processId =>
          (canWrite(processId) & post) {
            fileUpload("process") { case (_, byteSource) =>
              complete {
                MultipartUtils
                  .readFile(byteSource)
                  .flatMap(processService.importProcess(processId, _))
              }
            }
          }
        }
      } ~ path("processes" / ProcessNameSegment / "deployments") { processName =>
        processId(processName) { processId =>
          complete {
            // FIXME: We should provide Deployment definition and return there all deployments, not actions..
            processService.getProcessActions(processId.id)
          }
        }
      } ~ path("processes" / ProcessNameSegment) { processName =>
        processId(processName) { processId =>
          (delete & canWrite(processId)) {
            complete {
              processService
                .deleteProcess(processId)
                .withListenerNotifySideEffect(_ => OnDeleted(processId.id))
            }
          } ~ (put & canWrite(processId)) {
            entity(as[UpdateScenarioCommand]) { updateCommand =>
              canOverrideUsername(processId.id, updateCommand.forwardedUserName)(ec, user) {
                complete {
                  processService
                    .updateProcess(processId, updateCommand)
                    .withSideEffect(response =>
                      response.processResponse.foreach(resp => notifyListener(OnSaved(resp.id, resp.versionId)))
                    )
                    .map(_.validationResult)
                }
              }
            }
          } ~ (get & skipValidateAndResolveParameter & skipNodeResultsParameter) {
            (skipValidateAndResolve, skipNodeResults) =>
              complete {
                processService.getLatestProcessWithDetails(
                  processId,
                  GetScenarioWithDetailsOptions(
                    FetchScenarioGraph(validationFlagsToMode(skipValidateAndResolve, skipNodeResults)),
                    fetchState = true
                  )
                )
              }
          }
        }
      } ~ path("processes" / ProcessNameSegment / "rename" / ProcessNameSegment) { (processName, newName) =>
        (put & processId(processName)) { processId =>
          canWrite(processId) {
            complete {
              processService
                .renameProcess(processId, newName)
                .withListenerNotifySideEffect(response => OnRenamed(processId.id, response.oldName, response.newName))
            }
          }
        }
      } ~ path("processes" / ProcessNameSegment / VersionIdSegment) { (processName, versionId) =>
        (get & processId(processName) & skipValidateAndResolveParameter & skipNodeResultsParameter) {
          (processId, skipValidateAndResolve, skipNodeResults) =>
            complete {
              processService.getProcessWithDetails(
                processId,
                versionId,
                // TODO: disable fetching state when FE is ready
                GetScenarioWithDetailsOptions(
                  FetchScenarioGraph(validationFlagsToMode(skipValidateAndResolve, skipNodeResults)),
                  fetchState = true
                )
              )
            }
        }
      } ~ path("processes") {
        post {
          entity(as[CreateScenarioCommand]) { createCommand =>
            complete {
              processService
                .createProcess(createCommand)
                // Currently, we throw error but when we switch to Tapir, we would probably handle such a request validation errors more type-safety
                .map(_.valueOr(err => throw err))
                .withListenerNotifySideEffect(response => OnSaved(response.id, response.versionId))
                .map(response =>
                  HttpResponse(
                    status = StatusCodes.Created,
                    entity = HttpEntity(ContentTypes.`application/json`, response.asJson.noSpaces)
                  )
                )
            }
          }
        }
        // TODO: This is the legacy API, it should be removed in 1.15
      } ~ path("processes" / ProcessNameSegment / Segment) { (processName, category) =>
        authorize(user.can(category, Permission.Write)) {
          optionalHeaderValue(RemoteUserName.extractFromHeader) { remoteUserName =>
            canOverrideUsername(category, remoteUserName)(user) {
              parameters(Symbol("isFragment") ? false) { isFragment =>
                post {
                  complete {
                    processService
                      .createProcess(
                        CreateScenarioCommand(processName, Some(category), None, None, isFragment, remoteUserName)
                      )
                      // Currently, we throw error but when we switch to Tapir, we would probably handle such a request validation errors more type-safety
                      .map(_.valueOr(err => throw err))
                      .withListenerNotifySideEffect(response => OnSaved(response.id, response.versionId))
                      .map(response =>
                        HttpResponse(
                          status = StatusCodes.Created,
                          entity = HttpEntity(ContentTypes.`application/json`, response.asJson.noSpaces)
                        )
                      )
                  }
                }
              }
            }
          }
        }
      } ~ path("processes" / ProcessNameSegment / "status") { processName =>
        (get & processId(processName)) { processId =>
          complete {
            implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
            deploymentService.getProcessState(processId).map(ToResponseMarshallable(_))
          }
        }
      } ~ path("processes" / ProcessNameSegment / "toolbars") { processName =>
        (get & processId(processName)) { processId =>
          complete {
            processService
              .getLatestProcessWithDetails(processId, GetScenarioWithDetailsOptions.detailsOnly)
              .map(_.toEntity)
              .map(processToolbarService.getScenarioToolbarSettings)
          }
        }
      } ~ path("processes" / ProcessNameSegment / VersionIdSegment / "compare" / VersionIdSegment) {
        (processName, thisVersion, otherVersion) =>
          (get & processId(processName)) { processId =>
            complete {
              for {
                thisVersion <- processService.getProcessWithDetails(
                  processId,
                  thisVersion,
                  GetScenarioWithDetailsOptions.withsScenarioGraph
                )
                otherVersion <- processService.getProcessWithDetails(
                  processId,
                  otherVersion,
                  GetScenarioWithDetailsOptions.withsScenarioGraph
                )
              } yield ScenarioGraphComparator.compare(thisVersion.scenarioGraphUnsafe, otherVersion.scenarioGraphUnsafe)
            }
          }
      }
    }
  }

  private implicit class ListenerNotifyingFuture[T](future: Future[T]) {

    def withListenerNotifySideEffect(eventAction: T => ProcessChangeEvent)(implicit user: LoggedUser): Future[T] = {
      future.withSideEffect { value =>
        notifyListener(eventAction(value))
      }
    }

  }

  private def notifyListener(event: ProcessChangeEvent)(implicit user: LoggedUser): Unit = {
    implicit val listenerUser: User = ListenerApiUser(user)
    processChangeListener.handle(event)
  }

  private def processesQuery: Directive1[ScenarioQuery] = {
    parameters(
      Symbol("isFragment").as[Boolean].?,
      Symbol("isArchived").as[Boolean].?,
      Symbol("isDeployed").as[Boolean].?,
      Symbol("categories").as(CsvSeq[String]).?,
      Symbol("processingTypes").as(CsvSeq[String]).?,
      Symbol("names").as(CsvSeq[String]).?,
    ).tmap { case (isFragment, isArchived, isDeployed, categories, processingTypes, names) =>
      (isFragment, isArchived, isDeployed, categories, processingTypes, names.map(_.map(ProcessName(_))))
    }.as(ScenarioQuery.apply _)
  }

  private def skipValidateAndResolveParameter = {
    parameters(Symbol("skipValidateAndResolve").as[Boolean].withDefault(false))
  }

  private def skipNodeResultsParameter = {
    parameters(Symbol("skipNodeResults").as[Boolean].withDefault(false))
  }

  private def validationFlagsToMode(skipValidateAndResolve: Boolean, skipNodeResults: Boolean) = {
    if (skipValidateAndResolve) FetchScenarioGraph.DontValidate
    else FetchScenarioGraph.ValidateAndResolve(!skipNodeResults)
  }

}

object ProcessesResources {
  final case class ProcessUnmarshallingError(message: String) extends OtherError(message)

}
