package pl.touk.nussknacker.ui.api

import akka.http.scaladsl.model.{ContentTypeRange, ContentTypes, HttpResponse, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.util.Timeout
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.MetaData
import pl.touk.nussknacker.engine.definition.{ModelDataTestInfoProvider, TestInfoProvider, TestingCapabilities}
import pl.touk.nussknacker.engine.graph.node._
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.process.processingtypedata.ProcessingTypeDataProvider
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.process.test.ScenarioTestDataSerDe
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}

object TestInfoResources {

  def apply(providers: ProcessingTypeDataProvider[ModelData],
            processAuthorizer:AuthorizeProcess,
            processRepository: FetchingProcessRepository[Future],
            featuresOptions: FeatureTogglesConfig,
            scenarioTestDataSerDe: ScenarioTestDataSerDe,
            )
           (implicit ec: ExecutionContext): TestInfoResources =
    new TestInfoResources(
      providers.mapValues(new ModelDataTestInfoProvider(_)),
      processAuthorizer,
      processRepository,
      featuresOptions.testDataSettings,
      scenarioTestDataSerDe,
    )

}

class TestInfoResources(providers: ProcessingTypeDataProvider[TestInfoProvider],
                        val processAuthorizer:AuthorizeProcess,
                        val processRepository: FetchingProcessRepository[Future],
                        testDataSettings: TestDataSettings,
                        scenarioTestDataSerDe: ScenarioTestDataSerDe,
                       )
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
                    .getOrElse(TestingCapabilities.Disabled)
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
              complete(TestingCapabilities.Disabled)
            }
          }
        }
      }
    }
  }

  private def generateData(testSampleSize: Int, sourceOpt: Option[Source], provider: TestInfoProvider, metaData: MetaData): Either[String, Array[Byte]] = {
    for {
      _ <- Either.cond(testSampleSize <= testDataSettings.maxSamplesCount, (), s"Too many samples requested, limit is ${testDataSettings.maxSamplesCount}").right
      source <- sourceOpt.toRight("Scenario does not have source capable of generating test data")
      generatedData <- provider.generateTestData(metaData, source, testSampleSize).toRight("Test data could not be generated for scenario")
      rawTestData <- scenarioTestDataSerDe.serializeTestData(generatedData)
    } yield rawTestData.content
  }

}
