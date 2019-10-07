package pl.touk.nussknacker.ui.api

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.marshalling.Marshal
import akka.http.scaladsl.model.{HttpResponse, MessageEntity, StatusCodes}
import akka.http.scaladsl.server._
import akka.http.scaladsl.unmarshalling.{FromEntityUnmarshaller, Unmarshaller}
import akka.pattern.ask
import akka.stream.Materializer
import akka.util.Timeout
import com.carrotsearch.sizeof.RamUsageEstimator
import com.typesafe.scalalogging.LazyLogging
import de.heikoseeberger.akkahttpcirce.FailFastCirceSupport
import io.circe.generic.JsonCodec
import io.circe.parser.parse
import io.circe.syntax._
import io.circe.{Decoder, Encoder, Json}
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{ExceptionResult, ExpressionInvocationResult, MockedResult, NodeResult, ResultContext, TestData, TestResults}
import pl.touk.nussknacker.engine.api.{ArgonautCirce, Displayable}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.restmodel.displayedgraph.DisplayableProcess
import pl.touk.nussknacker.restmodel.process.ProcessIdWithName
import pl.touk.nussknacker.ui.api.ProcessesResources.UnmarshallError
import pl.touk.nussknacker.ui.config.FeatureTogglesConfig
import pl.touk.nussknacker.ui.process.deployment.{Cancel, Deploy, Snapshot, Test}
import pl.touk.nussknacker.ui.process.marshall.ProcessConverter
import pl.touk.nussknacker.ui.process.repository.FetchingProcessRepository
import pl.touk.nussknacker.ui.processreport.{NodeCount, ProcessCounter, RawCount}
import pl.touk.nussknacker.ui.security.api.LoggedUser

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}


object ManagementResources {

  def apply(processCounter: ProcessCounter,
            managementActor: ActorRef,
            testResultsMaxSizeInBytes: Int,
            processAuthorizator: AuthorizeProcess,
            processRepository: FetchingProcessRepository, featuresOptions: FeatureTogglesConfig)
           (implicit ec: ExecutionContext,
            mat: Materializer, system: ActorSystem): ManagementResources = {
    new ManagementResources(
      processCounter,
      managementActor,
      testResultsMaxSizeInBytes,
      processAuthorizator,
      processRepository,
      featuresOptions.deploySettings
    )
  }

  implicit val testResultsEncoder: Encoder[TestResults[Json]] = new Encoder[TestResults[Json]]() {

    implicit val nodeResult: Encoder[NodeResult[Json]] = io.circe.generic.semiauto.deriveEncoder
    implicit val expressionInvocationResult: Encoder[ExpressionInvocationResult[Json]] = io.circe.generic.semiauto.deriveEncoder
    implicit val mockedResult: Encoder[MockedResult[Json]] = io.circe.generic.semiauto.deriveEncoder
    implicit val resultContext: Encoder[ResultContext[Json]] = io.circe.generic.semiauto.deriveEncoder
    implicit val throwable: Encoder[Throwable] = Encoder[Option[String]].contramap(th => Option(th.getMessage))
    implicit val exceptionResult: Encoder[ExceptionResult[Json]] = io.circe.generic.semiauto.deriveEncoder

    override def apply(a: TestResults[Json]): Json = a match {
      case TestResults(nodeResults, invocationResults, mockedResults, exceptions, _) => Json.obj(
        "nodeResults" -> nodeResults.asJson,
        "invocationResults" -> invocationResults.asJson,
        "mockedResults" -> mockedResults.asJson,
        "exceptions" -> exceptions.asJson
      )
    }
  }

  val testResultsVariableEncoder : Any => io.circe.Json = {
    case displayable: Displayable =>
      def safeString(a: String) = Option(a).map(Json.fromString).getOrElse(Json.Null)
      val displayableJson = ArgonautCirce.toCirce(displayable.display)
      displayable.originalDisplay match {
        case None => Json.obj("pretty" -> displayableJson)
        case Some(original) => Json.obj("original" -> safeString(original), "pretty" -> displayableJson)
      }
    case a => Json.obj("pretty" -> BestEffortJsonEncoder(failOnUnkown = false).circeEncoder.apply(a))
  }


}

class ManagementResources(processCounter: ProcessCounter,
                          val managementActor: ActorRef,
                          testResultsMaxSizeInBytes: Int,
                          val processAuthorizer: AuthorizeProcess,
                          val processRepository: FetchingProcessRepository, deploySettings: Option[DeploySettings])
                         (implicit val ec: ExecutionContext, mat: Materializer, system: ActorSystem)
  extends Directives
    with LazyLogging
    with RouteWithUser
    with FailFastCirceSupport
    with AuthorizeProcessDirectives
    with ProcessDirectives {

  //TODO: in the future we could use https://github.com/akka/akka-http/pull/1828 when we can bump version to 10.1.x
  private val durationFromConfig = system.settings.config.getDuration("akka.http.server.request-timeout")
  private implicit val timeout: Timeout = Timeout(durationFromConfig.toMillis millis)
  private implicit final val plainBytes: FromEntityUnmarshaller[Array[Byte]] = Unmarshaller.byteArrayUnmarshaller
  private implicit final val plainString: FromEntityUnmarshaller[String] = Unmarshaller.stringUnmarshaller

  private def withComment: Directive1[Option[String]] =
    entity(as[Option[String]]).map(_.filterNot(_.isEmpty)).flatMap {
      case None if deploySettings.exists(_.requireComment) => reject(ValidationRejection("Comment is required", None))
      case comment => provide(comment)
    }

  def route(implicit user: LoggedUser): Route = {
    path("adminProcessManagement" / "snapshot" / Segment / Segment) { (processName, savepointDir) =>
      (post & processId(processName)) { processId =>
        canDeploy(processId) {
          complete {
            (managementActor ? Snapshot(processId, user, savepointDir))
              .mapTo[String].map(path => HttpResponse(entity = path, status = StatusCodes.OK))
              .recover(EspErrorToHttp.errorToHttp)
          }
        }
      }
    } ~
      path("adminProcessManagement" / "deploy" / Segment / Segment) { (processName, savepointPath) =>
        (post & processId(processName)) { processId =>
          canDeploy(processId) {
            withComment { comment =>
              complete {
                (managementActor ? Deploy(processId, user, Some(savepointPath), comment))
                  .map { _ => HttpResponse(status = StatusCodes.OK) }
                  .recover(EspErrorToHttp.errorToHttp)
              }
            }
          }
        }
      } ~
      path("processManagement" / "deploy" / Segment) { processName =>
        (post & processId(processName)) { processId =>
          canDeploy(processId){
            withComment { comment =>
              complete {
                (managementActor ? Deploy(processId, user, None, comment))
                  .map { _ => HttpResponse(status = StatusCodes.OK) }
                  .recover(EspErrorToHttp.errorToHttp)
              }
            }
          }
        }
      } ~
      path("processManagement" / "cancel" / Segment) { processName =>
        (post & processId(processName)) { processId =>
          canDeploy(processId) {
            withComment { comment =>
              complete {
                (managementActor ? Cancel(processId, user, comment))
                  .map { _ => HttpResponse(status = StatusCodes.OK) }
                  .recover(EspErrorToHttp.errorToHttp)
              }
            }
          }
        }
      } ~
      //TODO: maybe Write permission is enough here?
      path("processManagement" / "test" / Segment) { processName =>
        (post & processId(processName)) { processId =>
          canDeploy(processId) {
            //There is bug in akka-http in formFields, so we use custom toStrict method
            //issue: https://github.com/akka/akka/issues/19506
            //workaround: https://gist.github.com/rklaehn/d4d3ee43443b0f4741fb#file-uploadhandlertostrict-scala
            toStrict(5.second) {
              formFields('testData.as[Array[Byte]], 'processJson) { (testData, displayableProcessJson) =>
                complete {
                  performTest(processId, testData, displayableProcessJson).flatMap { results =>
                    try {
                      Marshal(results).to[MessageEntity].map(en => HttpResponse(entity = en))
                    }
                    catch {
                      //TODO There is some classloading issue here in Nussknacker that causes `results.asJson` throw NoClassDefFoundError
                      //We don't want to kill whole application in this case so we catch it. As soon as bug is fixed, we should remove this try/catch clause.
                      //Akka-http ExceptionHandler catches only NonFatal exceptions, so we cannot use it here...
                      //There is a test case for it in BaseFlowTest class
                      case t: NoClassDefFoundError =>
                        logger.error("Error during performing test", t)
                        Future.failed(t)
                    }
                  }.recover(EspErrorToHttp.errorToHttp)
                }
              }
            }
          }
        }
      }
  }

  private def performTest(id: ProcessIdWithName, testData: Array[Byte], displayableProcessJson: String)(implicit user: LoggedUser): Future[ResultsWithCounts] = {
    parse(displayableProcessJson).right.flatMap(Decoder[DisplayableProcess].decodeJson) match {
      case Right(process) =>
        val canonical = ProcessConverter.fromDisplayable(process)
        val canonicalJson = ProcessMarshaller.toJson(canonical).spaces2
        (managementActor ? Test(id, canonicalJson, TestData(testData), user, ManagementResources.testResultsVariableEncoder)).mapTo[TestResults[Json]].flatMap { results =>
          assertTestResultsAreNotTooBig(results)
        }.map { results =>
          ResultsWithCounts(ManagementResources.testResultsEncoder(results), computeCounts(canonical, results))
        }
      case Left(error) =>
        Future.failed(UnmarshallError(error.toString))
    }
  }

  private def assertTestResultsAreNotTooBig(testResults: TestResults[Json]): Future[TestResults[Json]] = {
    val testDataResultApproxByteSize = RamUsageEstimator.sizeOf(testResults)
    if (testDataResultApproxByteSize > testResultsMaxSizeInBytes) {
      logger.info(s"Test data limit exceeded. Approximate test data size: $testDataResultApproxByteSize, but limit is: $testResultsMaxSizeInBytes")
      Future.failed(new RuntimeException("Too much test data. Please decrease test input data size."))
    } else {
      Future.successful(testResults)
    }
  }

  private def computeCounts(canonical: CanonicalProcess, results: TestResults[_]) : Map[String, NodeCount] = {
    val counts = results.nodeResults.map { case (key, nresults) =>
      key -> RawCount(nresults.size.toLong, results.exceptions.find(_.nodeId.contains(key)).size.toLong)
    }
    processCounter.computeCounts(canonical, counts.get)
  }

  private def toStrict(timeout: FiniteDuration): Directive[Unit] = {
    def toStrict0(inner: Unit ⇒ Route): Route = {
      val result: RequestContext ⇒ Future[RouteResult] = c ⇒ {
        // call entity.toStrict (returns a future)
        c.request.entity.toStrict(timeout).flatMap { strict ⇒
          // modify the context with the strictified entity
          val c1 = c.withRequest(c.request.withEntity(strict))
          // call the inner route with the modified context
          inner(())(c1)
        }
      }
      result
    }
    Directive[Unit](toStrict0)
  }

}

@JsonCodec case class ResultsWithCounts(results: Json, counts: Map[String, NodeCount])
