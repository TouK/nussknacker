package pl.touk.nussknacker.engine.standalone.management

import java.util.concurrent.TimeUnit
import cats.data.Validated.{Invalid, Valid}
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import io.circe.Json
import pl.touk.nussknacker.engine.ModelData.ClasspathConfig
import pl.touk.nussknacker.engine.api.deployment.TestProcess.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.simple.SimpleProcessStateDefinitionManager
import pl.touk.nussknacker.engine.api.process.{ProcessName, RunMode, SourceTestSupport}
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.api.graph.EspProcess
import pl.touk.nussknacker.engine.api.graph.node.Source
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, ResultsCollectingListenerHolder, SinkInvocationCollector, TestDataPreparer, TestRunId, TestServiceInvocationCollector}
import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter
import pl.touk.nussknacker.engine.standalone.api.{StandaloneContextPreparer, StandaloneDeploymentData}
import pl.touk.nussknacker.engine.standalone.api.types._
import pl.touk.nussknacker.engine.standalone.metrics.NoOpMetricsProvider
import pl.touk.nussknacker.engine.util.Implicits.SourceIsReleasable
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.{ModelData, _}
import shapeless.Typeable
import shapeless.syntax.typeable._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Using

object StandaloneDeploymentManager {
  def apply(modelData: ModelData, config: Config) : StandaloneDeploymentManager = new StandaloneDeploymentManager(modelData, StandaloneProcessClient(config))
}

class StandaloneDeploymentManager(modelData: ModelData, client: StandaloneProcessClient)
  extends DeploymentManager with LazyLogging {

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  override def deploy(processVersion: ProcessVersion, deploymentData: DeploymentData, processDeploymentData: ProcessDeploymentData,
                      savepointPath: Option[String]): Future[Option[ExternalDeploymentId]] = {
    savepointPath match {
      case Some(_) => Future.failed(new UnsupportedOperationException("Cannot make savepoint on standalone scenario"))
      case None =>
        processDeploymentData match {
          case GraphProcess(processAsJson) =>
            client.deploy(StandaloneDeploymentData(processAsJson, System.currentTimeMillis(), processVersion, deploymentData)).map(_ => None)
          case CustomProcess(mainClass) =>
            Future.failed(new UnsupportedOperationException("custom scenario in standalone engine is not supported"))
        }
    }
  }

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] = {
    Future.failed(new UnsupportedOperationException("Cannot make savepoint on standalone scenario"))
  }

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] = {
    Future.failed(new UnsupportedOperationException("Cannot stop standalone scenario"))
  }

  override def test[T](processName: ProcessName, processJson: String, testData: TestData, variableEncoder: Any => T): Future[TestResults[T]] = {
    Future{
      //TODO: shall we use StaticMethodRunner here?
      modelData.withThisAsContextClassLoader {
        StandaloneTestMain.run(processJson, testData, modelData, variableEncoder)
      }
    }
  }

  override def findJobStatus(processName: ProcessName): Future[Option[ProcessState]] = {
    client.findStatus(processName)
  }

  override def cancel(name: ProcessName, user: User): Future[Unit] = {
    client.cancel(name)
  }

  override def processStateDefinitionManager: ProcessStateDefinitionManager = SimpleProcessStateDefinitionManager

  override def close(): Unit = {

  }

  override def customActions: List[CustomAction] = List.empty

  override def invokeCustomAction(actionRequest: CustomActionRequest,
                                  processDeploymentData: ProcessDeploymentData): Future[Either[CustomActionError, CustomActionResult]] =
    Future.successful(Left(CustomActionNotImplemented(actionRequest)))
}

object StandaloneTestMain {

  def run[T](processJson: String, testData: TestData, modelData: ModelData, variableEncoder: Any => T): TestResults[T] = {
    new StandaloneTestMain(
      testData = testData,
      process = TestUtils.readProcessFromArg(processJson),
      modelData).runTest(variableEncoder)
  }

}

class StandaloneTestMain(testData: TestData, process: EspProcess, modelData: ModelData) {

  private val timeout = FiniteDuration(10, TimeUnit.SECONDS)

  import ExecutionContext.Implicits.global

  def runTest[T](variableEncoder: Any => T): TestResults[T] = {
    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)
    val parsedTestData = new TestDataPreparer(modelData).prepareDataForTest(process, testData)


    //in tests we don't send metrics anywhere
    val testContext = new StandaloneContextPreparer(NoOpMetricsProvider)
    val runMode: RunMode = RunMode.Test

    //FIXME: validation??
    val standaloneInterpreter = StandaloneProcessInterpreter(process, testContext, modelData,
      additionalListeners = List(collectingListener), new TestServiceInvocationCollector(collectingListener.runId), runMode
    ) match {
      case Valid(interpreter) => interpreter
      case Invalid(errors) => throw new IllegalArgumentException("Error during interpreter preparation: " + errors.toList.mkString(", "))
    }

    try {
      val processVersion = ProcessVersion.empty.copy(processName = ProcessName("snapshot version")) // testing process may be unreleased, so it has no version
      val deploymentData = DeploymentData.empty
      standaloneInterpreter.open(JobData(process.metaData, processVersion, deploymentData))
      val results = Await.result(Future.sequence(parsedTestData.samples.map(standaloneInterpreter.invokeToResult(_, None))), timeout)
      collectSinkResults(collectingListener.runId, results)
      collectExceptions(collectingListener, results)
      collectingListener.results
    } finally {
      collectingListener.clean()
      standaloneInterpreter.close()
    }

  }

  private def collectSinkResults(runId: TestRunId, results: List[InterpretationResultType]): Unit = {
    //FIXME: testDataOutput is ignored here!
    val bestEffortJsonEncoder = BestEffortJsonEncoder(failOnUnkown = false, getClass.getClassLoader)
    val encodeFunction = (out: Any) => bestEffortJsonEncoder.encode(out).fold(
      "null",
      _.toString,
      _.toString,
      identity,
      array => Json.fromValues(array).spaces2,
      obj => Json.fromJsonObject(obj).spaces2
    )

    val successfulResults = results.flatMap(_.right.toOption.toList.flatten)
    successfulResults.foreach { result =>
      val node = result.reference.asInstanceOf[EndingReference].nodeId
      SinkInvocationCollector(runId, node, node, encodeFunction).collect(result)
    }
  }

  private def collectExceptions(listener: ResultsCollectingListener, results: List[InterpretationResultType]): Unit = {
    val exceptions = results.flatMap(_.left.toOption)
    exceptions.flatMap(_.toList).foreach(listener.exceptionThrown)
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

class StandaloneDeploymentManagerProvider extends DeploymentManagerProvider {

  override def createDeploymentManager(modelData: ModelData, config: Config): DeploymentManager =
    StandaloneDeploymentManager(modelData, config)

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def name: String = "requestResponseStandalone"

  override def emptyProcessMetadata(isSubprocess: Boolean): TypeSpecificData = StandaloneMetaData(None)

  override def supportsSignals: Boolean = false
}

object StandaloneDeploymentManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  def defaultTypeConfig(config: Config): ProcessingTypeConfig = {
    ProcessingTypeConfig("requestResponseStandalone",
                    config.as[ClasspathConfig]("standaloneConfig").urls,
                    config.getConfig("standaloneConfig"),
                    config.getConfig("standaloneProcessConfig"))
  }

}
