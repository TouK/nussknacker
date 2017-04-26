package pl.touk.esp.ui.api

import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.server._
import akka.util.Timeout
import pl.touk.esp.engine.definition.{TestInfoProvider, TestingCapabilities}
import pl.touk.esp.engine.graph.node.Source
import pl.touk.esp.ui.db.entity.ProcessEntity.ProcessingType.ProcessingType
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import pl.touk.esp.ui.util.Argonaut62Support
import shapeless.syntax.typeable._

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TestInfoResources(providers: Map[ProcessingType, TestInfoProvider],
                        processRepository: ProcessRepository)
                       (implicit ec: ExecutionContext) extends Directives with Argonaut62Support {
  import argonaut.ArgonautShapeless._
  import pl.touk.esp.ui.codec.UiCodecs._

  implicit val timeout = Timeout(1 minute)

  def route(implicit user: LoggedUser): Route = {
    authorize(user.hasPermission(Permission.Deploy)) {
      pathPrefix("testInfo") {
        post {
          entity(as[DisplayableProcess]) { displayableProcess =>
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
                    source.flatMap(processDefinition.generateTestData(metadata, _ , testSampleSize)).getOrElse(new Array[Byte](0))
                  resp
                }
              }
          }
        }
      }
    }
  }

}
