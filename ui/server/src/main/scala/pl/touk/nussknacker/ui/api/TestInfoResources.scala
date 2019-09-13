package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, HttpEntity, HttpResponse}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.definition.{ModelDataTestInfoProvider, TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository

object TestInfoResources {

  def apply(providers: Map[ProcessingType, ModelData], processAuthorizer:AuthorizeProcess, processRepository: FetchingProcessRepository)
           (implicit ec: ExecutionContext): TestInfoResources =
    new TestInfoResources(providers.mapValuesNow(new ModelDataTestInfoProvider(_)), processAuthorizer, processRepository)

}

class TestInfoResources(providers: Map[ProcessingType, TestInfoProvider],
                        val processAuthorizer:AuthorizeProcess,
                        val processRepository: FetchingProcessRepository)
                       (implicit val ec: ExecutionContext)
  extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  implicit val timeout: Timeout = Timeout(1 minute)

  private implicit final val bytes: FromEntityUnmarshaller[Array[Byte]] =
    Unmarshaller.byteArrayUnmarshaller.forContentTypes(ContentTypeRange(ContentTypes.`application/octet-stream`))

  def route(implicit user: LoggedUser): Route = {
    //TODO: is Write enough here?
    pathPrefix("testInfo") {
      post {
        entity(as[DisplayableProcess]) { displayableProcess =>
          processId(displayableProcess.id) { processId =>
            canDeploy(processId) {
              val processDefinition = providers(displayableProcess.processingType)

              val source = displayableProcess.nodes.flatMap(asSource).headOption
              val metadata = displayableProcess.metaData

              path("capabilities") {
                complete {
                  val resp: TestingCapabilities = source.map(processDefinition.getTestingCapabilities(metadata, _))
                    .getOrElse(TestingCapabilities(false, false))
                  resp
                }
              } ~
              path("generate" / IntNumber) { testSampleSize =>
                complete {
                  val resp: Array[Byte] =
                    source.flatMap(processDefinition.generateTestData(metadata, _, testSampleSize)).getOrElse(new Array[Byte](0))
                  HttpEntity(resp)
                }
              }
            }
          }
        }
      }
    }
  }
}