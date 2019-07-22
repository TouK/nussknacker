package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server._
import akka.util.Timeout
import pl.touk.nussknacker.engine.definition.{ModelDataTestInfoProvider, TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.engine.ProcessingTypeData.ProcessingType
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.http.argonaut.{Argonaut62Support, JsonMarshaller}
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.graph.source.SourceRef
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import shapeless.syntax.typeable._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import pl.touk.nussknacker.engine.util.Implicits._
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository

object TestInfoResources {

  def apply(providers: Map[ProcessingType, ModelData], processAuthorizer:AuthorizeProcess, processRepository: FetchingProcessRepository)
           (implicit ec: ExecutionContext, jsonMarshaller: JsonMarshaller): TestInfoResources =
    new TestInfoResources(providers.mapValuesNow(new ModelDataTestInfoProvider(_)), processAuthorizer, processRepository)

}

class TestInfoResources(providers: Map[ProcessingType, TestInfoProvider],
                        val processAuthorizer:AuthorizeProcess,
                        val processRepository: FetchingProcessRepository)
                       (implicit val ec: ExecutionContext, jsonMarshaller: JsonMarshaller)
  extends Directives
    with Argonaut62Support
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._

  implicit val timeout = Timeout(1 minute)

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
                  resp
                }
              }
            }
          }
        }
      }
    }
  }
}