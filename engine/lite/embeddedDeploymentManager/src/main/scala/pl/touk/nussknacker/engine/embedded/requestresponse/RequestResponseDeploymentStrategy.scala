package pl.touk.nussknacker.engine.embedded.requestresponse

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.Http.ServerBinding
import akka.http.scaladsl.server.Route
import akka.stream.Materializer
import cats.data.{NonEmptyList, Validated}
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.{JobData, MetaData, RequestResponseMetaData}
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.FatalUnknownError
import pl.touk.nussknacker.engine.api.deployment.DeploymentStatus
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.embedded.{Deployment, DeploymentStrategy}
import pl.touk.nussknacker.engine.embedded.requestresponse.RequestResponseDeploymentStrategy.slugForScenario
import pl.touk.nussknacker.engine.lite.api.runtimecontext.LiteEngineRuntimeContextPreparer
import pl.touk.nussknacker.engine.requestresponse.{RequestResponseConfig, RequestResponseRunnableScenarioInterpreter}

import scala.collection.concurrent.TrieMap
import scala.concurrent.{Await, ExecutionContext}
import scala.concurrent.duration.DurationInt
import scala.util.{Failure, Success, Try}

object RequestResponseDeploymentStrategy {

  import net.ceedubs.ficus.Ficus._
  import net.ceedubs.ficus.readers.ArbitraryTypeReader._

  def apply(config: Config)(implicit as: ActorSystem, ec: ExecutionContext): RequestResponseDeploymentStrategy = {
    new RequestResponseDeploymentStrategy(
      config.as[HttpBindingConfig]("http"),
      config.as[RequestResponseConfig]("request-response")
    )
  }

  def slugForScenario(metaData: MetaData): Validated[NonEmptyList[FatalUnknownError], String] =
    metaData.typeSpecificData match {
      case RequestResponseMetaData(slug) => Valid(slug.getOrElse(defaultSlug(metaData.name)))
      case _ => Invalid(NonEmptyList.of(FatalUnknownError(s"Wrong scenario metadata: ${metaData.typeSpecificData}")))
    }

  // should it be compatible with k8s version?
  def determineSlug(scenarioName: ProcessName, metaData: RequestResponseMetaData): String =
    metaData.slug.getOrElse(defaultSlug(scenarioName))

  def defaultSlug(scenarioName: ProcessName): String = UrlUtils.sanitizeUrlSlug(scenarioName.value)

}

class RequestResponseDeploymentStrategy(httpConfig: HttpBindingConfig, config: RequestResponseConfig)(
    implicit as: ActorSystem,
    ec: ExecutionContext
) extends DeploymentStrategy
    with LazyLogging {

  private val akkaHttpSetupTimeout = 10 seconds

  private val slugToScenarioRoute = TrieMap[String, Route]()

  private var server: ServerBinding = _

  override def open(modelData: ModelData, contextPreparer: LiteEngineRuntimeContextPreparer): Unit = {
    super.open(modelData, contextPreparer)
    logger.info(s"Serving request-response on ${httpConfig.port}")

    val route = new ScenarioDispatcherRoute(slugToScenarioRoute)

    implicit val materializer: Materializer = Materializer(as)
    server = Await.result(
      Http()
        .newServerAt(
          interface = httpConfig.interface,
          port = httpConfig.port
        )
        .bind(route.route),
      akkaHttpSetupTimeout
    )
  }

  override def close(): Unit = {
    Await.result(server.terminate(akkaHttpSetupTimeout), akkaHttpSetupTimeout)
  }

  override def onScenarioAdded(jobData: JobData, parsedResolvedScenario: CanonicalProcess)(
      implicit ec: ExecutionContext
  ): Try[RequestResponseDeployment] = synchronized {
    // RequestResponseScenarioInterpreter is 'opened' in constructor of RequestResponseRunnableScenarioInterpreter
    lazy val interpreterTry = Try(
      new RequestResponseRunnableScenarioInterpreter(
        jobData,
        parsedResolvedScenario,
        modelData,
        contextPreparer,
        config
      )
    )

    for {
      slug <- slugForScenario(jobData.metaData)
        .fold(errors => Failure(new IllegalArgumentException(errors.toString())), Success(_))
      interpreter <- interpreterTry
      route <- Try(
        interpreter.routes.getOrElse(
          throw new RuntimeException("Route should always be defined for Request-Response interpreter")
        )
      )
    } yield {
      slugToScenarioRoute += (slug -> route)
      new RequestResponseDeployment(slug, interpreter)
    }
  }

  class RequestResponseDeployment(path: String, interpreter: RequestResponseRunnableScenarioInterpreter)
      extends Deployment {

    override def status(): DeploymentStatus = DeploymentStatus.Running

    override def close(): Unit = {
      slugToScenarioRoute.remove(path)
      interpreter.close()
    }

  }

}
