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
  CreateProcessCommand,
  FetchScenarioGraph,
  GetScenarioWithDetailsOptions,
  UpdateProcessCommand
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
    processToolbarService: ProcessToolbarService,
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
      } ~ path("unarchive" / Segment) { processName =>
        (post & processId(processName)) { processId =>
          canWrite(processId) {
            complete {
              processService
                .unArchiveProcess(processId)
                .withListenerNotifySideEffect(_ => OnUnarchived(processId.id))
            }
          }
        }
      } ~ path("archive" / Segment) { processName =>
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
        (get & processesQuery & skipValidateAndResolveParameter) { (query, skipValidateAndResolve) =>
          complete {
            processService.getLatestProcessesWithDetails(
              query,
              GetScenarioWithDetailsOptions(FetchScenarioGraph(!skipValidateAndResolve), fetchState = false)
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
      } ~ path("processes" / "import" / Segment) { processName =>
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
      } ~ path("processes" / Segment / "deployments") { processName =>
        processId(processName) { processId =>
          complete {
            // FIXME: We should provide Deployment definition and return there all deployments, not actions..
            processService.getProcessActions(processId.id)
          }
        }
      } ~ path("processes" / Segment) { processName =>
        processId(processName) { processId =>
          (delete & canWrite(processId)) {
            complete {
              processService
                .deleteProcess(processId)
                .withListenerNotifySideEffect(_ => OnDeleted(processId.id))
            }
          } ~ (put & canWrite(processId)) {
            entity(as[UpdateProcessCommand]) { updateCommand =>
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
          } ~ (get & skipValidateAndResolveParameter) { skipValidateAndResolve =>
            complete {
              processService.getLatestProcessWithDetails(
                processId,
                GetScenarioWithDetailsOptions(FetchScenarioGraph(!skipValidateAndResolve), fetchState = true)
              )
            }
          }
        }
      } ~ path("processes" / Segment / "rename" / Segment) { (processName, newName) =>
        (put & processId(processName)) { processId =>
          canWrite(processId) {
            complete {
              processService
                .renameProcess(processId, ProcessName(newName))
                .withListenerNotifySideEffect(response => OnRenamed(processId.id, response.oldName, response.newName))
            }
          }
        }
      } ~ path("processes" / Segment / VersionIdSegment) { (processName, versionId) =>
        (get & processId(processName) & skipValidateAndResolveParameter) { (processId, skipValidateAndResolve) =>
          complete {
            processService.getProcessWithDetails(
              processId,
              versionId,
              // TODO: disable fetching state when FE is ready
              GetScenarioWithDetailsOptions(FetchScenarioGraph(!skipValidateAndResolve), fetchState = true)
            )
          }
        }
      } ~ path("processes" / Segment / Segment) { (processName, category) =>
        authorize(user.can(category, Permission.Write)) {
          optionalHeaderValue(RemoteUserName.extractFromHeader) { remoteUserName =>
            canOverrideUsername(category, remoteUserName)(user) {
              parameters(Symbol("isFragment") ? false) { isFragment =>
                post {
                  complete {
                    processService
                      .createProcess(
                        CreateProcessCommand(ProcessName(processName), category, isFragment, remoteUserName)
                      )
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
      } ~ path("processes" / Segment / "status") { processName =>
        (get & processId(processName)) { processId =>
          complete {
            implicit val freshnessPolicy: DataFreshnessPolicy = DataFreshnessPolicy.Fresh
            deploymentService.getProcessState(processId).map(ToResponseMarshallable(_))
          }
        }
      } ~ path("processes" / Segment / "toolbars") { processName =>
        (get & processId(processName)) { processId =>
          complete {
            processService
              .getLatestProcessWithDetails(processId, GetScenarioWithDetailsOptions.detailsOnly)
              .map(_.toEntity)
              .map(processToolbarService.getProcessToolbarSettings)
          }
        }
      } ~ path("processes" / "category" / Segment / Segment) { (processName, category) =>
        (post & processId(processName)) { processId =>
          hasAdminPermission(user) {
            complete {
              processService
                .updateCategory(processId, category)
                .withListenerNotifySideEffect(response =>
                  OnCategoryChanged(processId.id, response.oldCategory, response.newCategory)
                )
            }
          }
        }
      } ~ path("processes" / Segment / VersionIdSegment / "compare" / VersionIdSegment) {
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
              } yield ProcessComparator.compare(thisVersion.scenarioGraphUnsafe, otherVersion.scenarioGraphUnsafe)
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

}

object ProcessesResources {
  final case class ProcessUnmarshallingError(message: String) extends OtherError(message)

}
