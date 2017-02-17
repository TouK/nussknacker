package pl.touk.esp.ui.api

import akka.actor.ActorRef
import akka.http.scaladsl.marshalling.ToResponseMarshallable
import akka.http.scaladsl.model.{ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server.{Directives, Route}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import argonaut.Argonaut._
import argonaut.PrettyParams
import com.typesafe.scalalogging.LazyLogging
import pl.touk.esp.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.esp.engine.definition.DefinitionExtractor.PlainClazzDefinition
import pl.touk.esp.ui.api.ProcessesResources.UnmarshallError
import pl.touk.esp.ui.codec.UiCodecs
import pl.touk.esp.ui.process.deployment.{Cancel, Deploy, Test}
import pl.touk.esp.ui.process.displayedgraph.DisplayableProcess
import pl.touk.esp.ui.process.marshall.{ProcessConverter, UiProcessMarshaller}
import pl.touk.esp.ui.process.repository.ProcessRepository
import pl.touk.esp.ui.security.{LoggedUser, Permission}
import pl.touk.esp.ui.util.MultipartUtils

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._


class ManagementResources(typesInformation: List[PlainClazzDefinition],
                          val managementActor: ActorRef)(implicit ec: ExecutionContext, mat: Materializer) extends Directives with LazyLogging {

  import pl.touk.esp.ui.codec.UiCodecs.displayableProcessCodec
  val codecs = UiCodecs.ContextCodecs(typesInformation)
  val processMarshaller = UiProcessMarshaller()

  import codecs._

  implicit val timeout = Timeout(1 minute)

  def route(implicit user: LoggedUser): Route = {
    authorize(user.hasPermission(Permission.Deploy)) {
      path("processManagement" / "deploy" / Segment) { processId =>
        post {
          complete {
            (managementActor ? Deploy(processId, user))
              .map { _ => HttpResponse(status = StatusCodes.OK) }
              .recover(EspErrorToHttp.errorToHttp)
          }
        }
      } ~
        path("processManagement" / "cancel" / Segment) { processId =>
          post {
            complete {
              (managementActor ? Cancel(processId, user))
                .map { _ => HttpResponse(status = StatusCodes.OK) }
                .recover(EspErrorToHttp.errorToHttp)
            }
          }
        } ~
        path("processManagement" / "test" / Segment) { processId =>
          post {
            fileUpload("testData") { case (metadata, byteSource) =>
              fileUpload("processJson") { case (processJsonMetadata, processJsonByteSource) =>
                complete {
                  MultipartUtils.readFile(byteSource).map[ToResponseMarshallable] { testData =>
                    MultipartUtils.readFile(processJsonByteSource).map[ToResponseMarshallable] { displayableProcessJson =>
                      performTest(processId, testData, displayableProcessJson).map { results =>
                        HttpResponse(status = StatusCodes.OK, entity =
                          HttpEntity(ContentTypes.`application/json`, results.asJson.pretty(PrettyParams.spaces2)))
                      }.recover(EspErrorToHttp.errorToHttp)
                    }
                  }
                }
              }
            }
          }
        }
    }
  }

  private def performTest(processId: String, testData: String, displayableProcessJson: String)(implicit user: LoggedUser): Future[TestResults] = {
    displayableProcessJson.decodeEither[DisplayableProcess] match {
      case Right(process) =>
        val canonical = ProcessConverter.fromDisplayable(process)
        val canonicalJson = processMarshaller.toJson(canonical, PrettyParams.nospace)
        (managementActor ? Test(processId, canonicalJson, TestData(testData), user)).mapTo[TestResults]
      case Left(error) =>
        Future.failed(UnmarshallError(error))
    }
  }


}
