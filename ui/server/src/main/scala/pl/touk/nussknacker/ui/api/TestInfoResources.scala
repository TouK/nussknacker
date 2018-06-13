package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server._
import akka.util.Timeout
import pl.touk.nussknacker.engine.definition.{ModelDataTestInfoProvider, TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.nussknacker.ui.process.displayedgraph.DisplayableProcess
import pl.touk.http.argonaut.Argonaut62Support
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.ui.security.api.{LoggedUser, Permission}
import shapeless.syntax.typeable._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._
import pl.touk.nussknacker.engine.util.Implicits._

object TestInfoResources {

  def apply(providers: Map[ProcessingType, ModelData], processAuthorizer:AuthorizeProcess)(implicit ec: ExecutionContext): TestInfoResources =
    new TestInfoResources(providers.mapValuesNow(new ModelDataTestInfoProvider(_)), processAuthorizer)

}

class TestInfoResources(providers: Map[ProcessingType, TestInfoProvider], val processAuthorizer:AuthorizeProcess)
                       (implicit ec: ExecutionContext)
  extends Directives
    with Argonaut62Support
    with RouteWithUser
    with AuthorizeProcessDirectives {

  import argonaut.ArgonautShapeless._
  import pl.touk.nussknacker.ui.codec.UiCodecs._

  implicit val timeout = Timeout(1 minute)

  def route(implicit user: LoggedUser): Route = {
    //TODO: is Write enough here?
    pathPrefix("testInfo") {
      post {
        entity(as[DisplayableProcess]) { displayableProcess =>
          canDeploy(displayableProcess.id) {

            val processDefinition = providers(displayableProcess.processingType)

            val source = displayableProcess.nodes.flatMap(_.cast[Source]).headOption
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