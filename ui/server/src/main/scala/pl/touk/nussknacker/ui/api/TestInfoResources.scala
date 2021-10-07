package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, HttpEntity, HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.definition.{ModelDataTestInfoProvider, TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.api.graph.node._
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository

object TestInfoResources {

  def apply(providers: ProcessingTypeDataProvider[ModelData],
            processAuthorizer:AuthorizeProcess,
            processRepository: FetchingProcessRepository[Future],
            featuresOptions: FeatureTogglesConfig,
            )
           (implicit ec: ExecutionContext): TestInfoResources =
    new TestInfoResources(providers.mapValues(new ModelDataTestInfoProvider(_)), processAuthorizer, processRepository, featuresOptions.testDataSettings)

}

class TestInfoResources(providers: ProcessingTypeDataProvider[TestInfoProvider],
                        val processAuthorizer:AuthorizeProcess,
                        val processRepository: FetchingProcessRepository[Future],
                        testDataSettings: TestDataSettings)
                       (implicit val ec: ExecutionContext)
  extends Directives
    with FailFastCirceSupport
    with RouteWithUser
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  implicit val timeout: Timeout = Timeout(1 minute)

  private implicit final val bytes: FromEntityUnmarshaller[Array[Byte]] =
    Unmarshaller.byteArrayUnmarshaller.forContentTypes(ContentTypeRange(ContentTypes.`application/octet-stream`))

  def securedRoute(implicit user: LoggedUser): Route = {
    //TODO: is Write enough here?
    pathPrefix("testInfo") {
      post {
        entity(as[DisplayableProcess]) { displayableProcess =>
          processId(displayableProcess.id) { processId =>
            canDeploy(processId) {
              val provider = providers.forTypeUnsafe(displayableProcess.processingType)

              val source = displayableProcess.nodes.flatMap(asSource).headOption
              val metadata = displayableProcess.metaData

              path("capabilities") {
                complete {
                  val resp: TestingCapabilities = source.map(provider.getTestingCapabilities(metadata, _))
                    .getOrElse(TestingCapabilities(false, false))
                  resp
                }
              } ~
              path("generate" / IntNumber) { testSampleSize =>
                complete {
                  generateData(testSampleSize, source, provider, metadata) match {
                    case Left(error) => HttpResponse(StatusCodes.BadRequest, entity = error)
                    case Right(data) => HttpResponse(entity = data)
                  }
                }
              }
            } ~ path("capabilities") {
              complete(TestingCapabilities(false, false))
            }
          }
        }
      }
    }
  }

  def generateData(testSampleSize: Int, sourceOpt: Option[Source], provider: TestInfoProvider, metaData: MetaData): Either[String, Array[Byte]] = {
    if (testSampleSize > testDataSettings.maxSamplesCount) {
      return Left(s"Too many samples requested, limit is ${testDataSettings.maxSamplesCount}")
    }
    val generatedData = sourceOpt match {
      case Some(source) => provider.generateTestData(metaData, source, testSampleSize).getOrElse(Array())
      case None => return Left("Scenario does not have source capable of generating test data")
    }
    if (generatedData.length > testDataSettings.testDataMaxBytes) {
      Left(s"Too much data generated, limit is ${testDataSettings.testDataMaxBytes}")
    } else {
      Right(generatedData)
    }
  }

}
