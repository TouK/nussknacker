package pl.touk.nussknacker.engine.requestresponse.management

import akka.actor.ActorSystem
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.{DeploymentManagerProvider, ModelData, ProcessingTypeConfig, TypeSpecificInitialData}
import pl.touk.nussknacker.engine.ModelData.ClasspathConfig
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.process.ProcessName
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.requestresponse.RequestResponseEngine
import pl.touk.nussknacker.engine.requestresponse.api.RequestResponseDeploymentData
import pl.touk.nussknacker.engine.util.Implicits.SourceIsReleasable
import sttp.client.{NothingT, SttpBackend}

import scala.concurrent.{ExecutionContext, Future}
import scala.util.Using

object RequestResponseDeploymentManager {
  def apply(modelData: ModelData, config: Config)(implicit ec: ExecutionContext, sttpBackend: SttpBackend[Future, Nothing, NothingT]): RequestResponseDeploymentManager =
    new RequestResponseDeploymentManager(modelData, RequestResponseClient(config))
}

class RequestResponseDeploymentManager(modelData: ModelData, client: RequestResponseClient)(implicit ec: ExecutionContext)
  extends BaseDeploymentManager with LazyLogging {

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, processDeploymentData: ProcessDeploymentData,
                      savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    savepointPath match {
      case Some(_) => Future.failed(new UnsupportedOperationException("Cannot make savepoint on request-response scenario"))
      case None =>
        processDeploymentData match {
          case GraphProcess(processAsJson) =>
            client.deploy(RequestResponseDeploymentData(processAsJson, System.currentTimeMillis(), processVersion, deploymentData)).map(_ => None)
          case CustomProcess(_) =>
            Future.failed(new UnsupportedOperationException("custom scenario in request-response engine is not supported"))
        }
    }
  }

  override def test[T](processName: ProcessName, processJson: String, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]] = {
    Future{
      //TODO: shall we use StaticMethodRunner here?
      modelData.withThisAsContextClassLoader {
        val espProcess = TestUtils.readProcessFromArg(processJson)
        RequestResponseEngine.testRunner.runTest(modelData, testData, espProcess, variableEncoder)
      }
    }
  }

  override def findJobStatus(processName: ProcessName): Future[Option[ProcessState]] = {
    client.findStatus(processName)
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    client.cancel(name)
  }

}

//FIXME deduplicate with pl.touk.nussknacker.engine.process.runner.FlinkRunner?
// maybe we should test processes via HTTP instead of reflection?
object TestUtils {

  def readProcessFromArg(arg: String): EspProcess = {
    val canonicalJson = if (arg.startsWith("@")) {
      Using.resource(scala.io.Source.fromFile(arg.substring(1)))(_.mkString)
    } else {
      arg
    }
    ProcessMarshaller.fromJson(canonicalJson).toValidatedNel[Any, CanonicalProcess] andThen { canonical =>
      ProcessCanonizer.uncanonize(canonical)
    } match {
      case Valid(p) => p
      case Invalid(err) => throw new IllegalArgumentException(err.toList.mkString("Unmarshalling errors: ", ", ", ""))
    }
  }

}

class RequestResponseDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: ModelData, config: Config)
                                      (implicit ec: ExecutionContext, actorSystem: ActorSystem, sttpBackend: SttpBackend[Future, Nothing, NothingT]): DeploymentManager =
    RequestResponseDeploymentManager(modelData, config)

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def name: String = "requestResponse"

  override def typeSpecificInitialData: TypeSpecificInitialData = TypeSpecificInitialData(StandaloneMetaData(None))

  override def supportsSignals: Boolean = false
}

object RequestResponseDeploymentManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  def defaultTypeConfig(config: Config): ProcessingTypeConfig = {
    ProcessingTypeConfig("requestResponse",
                    config.as[ClasspathConfig]("modelConfig").urls,
                    config.getConfig("deploymentConfig"),
                    config.getConfig("modelConfig"))
  }

}
