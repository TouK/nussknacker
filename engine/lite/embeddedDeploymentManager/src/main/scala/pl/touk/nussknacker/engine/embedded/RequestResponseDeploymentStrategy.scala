package pl.touk.nussknacker.engine.embedded

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.model._
import akka.stream.Materializer
import cats.data.Validated.{Invalid, Valid}
import cats.data.{NonEmptyList, Validated}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.syntax._
import net.ceedubs.ficus.Ficus._
import net.ceedubs.ficus.readers.ArbitraryTypeReader.arbitraryTypeValueReader
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FatalUnknownError
import pl.touk.nussknacker.engine.api.deployment.StateStatus
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleStateStatus
import pl.touk.nussknacker.engine.api.process.ComponentUseCase
import pl.touk.nussknacker.engine.api.{JobData, MetaData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.embedded.RequestResponseDeploymentStrategy.RequestResponseConfig
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.lite.TestRunner
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.lite.requestresponse.ProcessRoute
import pl.touk.nussknacker.engine.requestresponse.{FutureBasedRequestResponseScenarioInterpreter, RequestResponseInterpreter}
import pl.touk.nussknacker.engine.resultcollector.ProductionServiceInvocationCollector
import pl.touk.nussknacker.engine.util.config.ConfigEnrichments._

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration.DurationInt
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Failure, Success, Try}

object RequestResponseDeploymentStrategy {

  case class RequestResponseConfig(port: Int, interface: String = "0.0.0.0")

  def apply(config: Config)(implicit as: ActorSystem): RequestResponseDeploymentStrategy = {
    new RequestResponseDeploymentStrategy(config.rootAs[RequestResponseConfig])
  }

}

class RequestResponseDeploymentStrategy(config: RequestResponseConfig)(implicit as: ActorSystem)
  extends DeploymentStrategy with LazyLogging {

  private val akkaHttpSetupTimeout = 10 seconds

  private val pathToInterpreter = TrieMap[String, FutureBasedRequestResponseScenarioInterpreter.InterpreterType]()

  private var server: ServerBinding = _

  override type ScenarioInterpreter = RequestResponseDeployment

  override def open(modelData: ModelData, contextPreparer: LiteEngineRuntimeContextPreparer): Unit = {
    super.open(modelData, contextPreparer)
    logger.info(s"Serving request-response on ${config.port}")

    val route = new ProcessRoute(pathToInterpreter)

    import as.dispatcher
    implicit val materializer: Materializer = Materializer(as)

    server = Await.result(
      Http().newServerAt(
      interface = config.interface,
      port = config.port
    ).bind(route.route), akkaHttpSetupTimeout)
  }

  override def close(): Unit = {
    Await.result(server.terminate(akkaHttpSetupTimeout), akkaHttpSetupTimeout)
  }


  override def onScenarioAdded(jobData: JobData,
                               parsedResolvedScenario: EspProcess)(implicit ec: ExecutionContext): Try[RequestResponseDeployment] = synchronized {
    import pl.touk.nussknacker.engine.requestresponse.FutureBasedRequestResponseScenarioInterpreter._

    val interpreter = RequestResponseInterpreter[Future](parsedResolvedScenario, jobData.processVersion, contextPreparer, modelData, Nil,
      ProductionServiceInvocationCollector, ComponentUseCase.EngineRuntime)
    val interpreterWithPath = pathForScenario(jobData.metaData).product(interpreter)
    interpreterWithPath.foreach { case (path, interpreter) =>
      pathToInterpreter += (path -> interpreter)
      interpreter.open()
    }
    interpreterWithPath
      .map { case (path, deployment) => new RequestResponseDeployment(path, deployment) }
      .fold(errors => Failure(new IllegalArgumentException(errors.toString())), Success(_))
  }

  private def pathForScenario(metaData: MetaData): Validated[NonEmptyList[FatalUnknownError], String] = metaData.typeSpecificData match {
    case RequestResponseMetaData(path) => Valid(path.getOrElse(metaData.id))
    case _ => Invalid(NonEmptyList.of(FatalUnknownError(s"Wrong scenario metadata: ${metaData.typeSpecificData}")))
  }

  override def testRunner(implicit ec: ExecutionContext): TestRunner = FutureBasedRequestResponseScenarioInterpreter.testRunner

  class RequestResponseDeployment(path: String,
                                  interpreter: FutureBasedRequestResponseScenarioInterpreter.InterpreterType) extends Deployment {

    override def readStatus(): StateStatus = SimpleStateStatus.Running

    override def close(): Unit = {
      pathToInterpreter.remove(path)
      interpreter.close()
    }
  }


}

