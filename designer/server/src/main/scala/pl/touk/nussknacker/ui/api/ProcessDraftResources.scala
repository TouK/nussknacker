package pl.touk.nussknacker.ui.api

import com.github.pjfanning.pekkohttpcirce.FailFastCirceSupport
import org.apache.pekko.http.scaladsl.model.StatusCodes
import org.apache.pekko.http.scaladsl.server._
import pl.touk.nussknacker.ui.process.draft.ProcessDraftService
import pl.touk.nussknacker.ui.process.draft.ProcessDraftService.SaveDraftCommand
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext

class ProcessDraftResources(
    draftService: ProcessDraftService,
    val processAuthorizer: AuthorizeProcess,
)(implicit val ec: ExecutionContext)
    extends Directives
    with FailFastCirceSupport
    with NuPathMatchers
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  def securedRoute(implicit user: LoggedUser): Route =
    path("processes" / ProcessNameSegment / "draft") { processName =>
      processId(processName) { processId =>
        get {
          complete {
            draftService.getDraft(processId)
          }
        } ~ (put & canWrite(processId)) {
          entity(as[SaveDraftCommand]) { cmd =>
            complete {
              draftService.saveDraft(processId, cmd)
            }
          }
        } ~ (delete & canWrite(processId)) {
          complete {
            draftService.deleteDraft(processId).map(_ => StatusCodes.NoContent -> "")
          }
        }
      }
    }

}
