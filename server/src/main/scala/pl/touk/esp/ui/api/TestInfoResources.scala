package pl.touk.esp.ui.api

import akka.http.scaladsl.model.{HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.util.Timeout
import cats.data.Validated.{Invalid, Valid}
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.definition.TestInfoProvider
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.ProcessConverter
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import pl.touk.esp.ui.util.Argonaut62Support

import scala.concurrent.ExecutionContext
import scala.concurrent.duration._

class TestInfoResources(processDefinition: TestInfoProvider, testSampleSize: Int)
                       (implicit ec: ExecutionContext) extends Directives with Argonaut62Support {
  import argonaut.ArgonautShapeless._
  import pl.touk.esp.ui.codec.UiCodecs._

  implicit val timeout = Timeout(1 minute)

  def route(implicit user: LoggedUser): Route = {
    authorize(user.hasPermission(Permission.Deploy)) {
      pathPrefix("testInfo") {
        post {
          entity(as[DisplayableProcess]) { displayableProcess =>
            val espProcess = ProcessCanonizer.uncanonize(ProcessConverter.fromDisplayable(displayableProcess))

            path("capabilities") {
              complete {
                espProcess match {
                  case Valid(process) => processDefinition.getTestingCapabilities(process)
                  case Invalid(error) => HttpResponse(status = StatusCodes.BadRequest, entity = "Failed to uncanonize")
                }
              }
            } ~
              path("generate") {
                complete {
                  espProcess match {
                    case Valid(process) => val response = processDefinition.generateTestData(process, testSampleSize).getOrElse(new Array[Byte](0))
                      response
                    case Invalid(error) => HttpResponse(status = StatusCodes.BadRequest, entity = "Failed to uncanonize")
                  }
                }
              }
          }
        }
      }
    }
  }

}
