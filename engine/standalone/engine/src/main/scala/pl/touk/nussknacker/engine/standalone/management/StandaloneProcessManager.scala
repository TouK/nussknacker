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
import pl.touk.nussknacker.engine.api.process.{ProcessName, ProcessObjectDependencies, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.queryablestate.QueryableClient
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{ServiceInvocationCollector, SinkInvocationCollector}
import pl.touk.nussknacker.engine.api.test.{ResultsCollectingListener, ResultsCollectingListenerHolder, TestRunId}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.{ObjectWithMethodDef, OverriddenObjectWithMethodDef}
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition._
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.standalone.StandaloneProcessInterpreter
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import pl.touk.nussknacker.engine.standalone.api.types._
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer
import pl.touk.nussknacker.engine.standalone.utils.metrics.NoOpMetricsProvider
import pl.touk.nussknacker.engine.util.json.BestEffortJsonEncoder
import pl.touk.nussknacker.engine.{ModelData, _}
import shapeless.Typeable
import shapeless.syntax.typeable._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

object StandaloneProcessManager {
  def apply(modelData: ModelData, config: Config) : StandaloneProcessManager = new StandaloneProcessManager(modelData, StandaloneProcessClient(config))
}

class StandaloneProcessManager(modelData: ModelData, client: StandaloneProcessClient)
  extends ProcessManager with LazyLogging {

  private implicit val ec: ExecutionContext = ExecutionContext.Implicits.global

  override def deploy(processVersion: ProcessVersion, processDeploymentData: ProcessDeploymentData,
                      savepointPath: Option[String], user: User): Future[Unit] = {
    savepointPath match {
      case Some(_) => Future.failed(new UnsupportedOperationException("Cannot make savepoint on standalone process"))
      case None =>
        processDeploymentData match {
          case GraphProcess(processAsJson) =>
            client.deploy(DeploymentData(processAsJson, System.currentTimeMillis(), processVersion))
          case CustomProcess(mainClass) =>
            Future.failed(new UnsupportedOperationException("custom process in standalone engine is not supported"))
        }
    }
  }

  override def savepoint(name: ProcessName, savepointDir: Option[String]): Future[SavepointResult] = {
    Future.failed(new UnsupportedOperationException("Cannot make savepoint on standalone process"))
  }

  override def stop(name: ProcessName, savepointDir: Option[String], user: User): Future[SavepointResult] = {
    Future.failed(new UnsupportedOperationException("Cannot stop standalone process"))
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

  override def customAction(customAction: CustomAction): Future[Either[CustomActionError, CustomActionResult]] = Future.successful {
    Left(CustomActionError("Not implemented"))
  }
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
    val creator = modelData.configCreator
    val processObjectDependencies = ProcessObjectDependencies(modelData.processConfig, modelData.objectNaming)

    val definitions = ProcessDefinitionExtractor.extractObjectWithMethods(creator, processObjectDependencies)

    val collectingListener = ResultsCollectingListenerHolder.registerRun(variableEncoder)

    //in tests we don't send metrics anywhere
    val testContext = new StandaloneContextPreparer(NoOpMetricsProvider)

    //FIXME: validation??
    val standaloneInterpreter = StandaloneProcessInterpreter(process, testContext, modelData,
      definitionsPostProcessor = prepareMocksForTest(collectingListener),
      additionalListeners = List(collectingListener)
    ) match {
      case Valid(interpreter) => interpreter
      case Invalid(errors) => throw new IllegalArgumentException("Error during interpreter preparation: " + errors.toList.mkString(", "))
    }

    val parsedTestData = readTestData(definitions, standaloneInterpreter.source)

    try {
      val processVersion = ProcessVersion.empty.copy(processName = ProcessName("snapshot version")) // testing process may be unreleased, so it has no version
      standaloneInterpreter.open(JobData(process.metaData, processVersion))
      val results = Await.result(Future.sequence(parsedTestData.map(standaloneInterpreter.invokeToResult(_, None))), timeout)
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
    val bestEffortJsonEncoder = BestEffortJsonEncoder(failOnUnkown = false)
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


  private def readTestData(definitions: ProcessDefinition[ObjectWithMethodDef], sourceObj: api.process.Source[Any]): List[Any] = {
    //FIXME: asInstanceOf, should be proper handling of SubprocessInputDefinition
    val sourceType = process.roots.head.data.asInstanceOf[Source].ref.typ
    val testDataParser = sourceObj
      .cast[TestDataParserProvider[_]](Typeable.simpleTypeable(classOf[TestDataParserProvider[_]]))
      .map(_.testDataParser)
      .getOrElse(throw new IllegalArgumentException(s"Source $sourceType cannot be tested"))
    val parsedTestData = testDataParser.parseTestData(testData.testData)
    parsedTestData
  }

  private def prepareMocksForTest(listener: ResultsCollectingListener)(definitions: ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef]): ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef] = {
    import pl.touk.nussknacker.engine.util.Implicits._
    val servicesWithEnabledInvocationCollector = definitions.services.mapValuesNow { service =>
      TestUtils.prepareServiceWithEnabledInvocationCollector(listener.runId, service)
    }
    definitions
      .copy(services = servicesWithEnabledInvocationCollector)
  }

}

//FIXME deduplicate with pl.touk.nussknacker.engine.process.runner.FlinkRunner?
// maybe we should test processes via HTTP instead of reflection?
object TestUtils {

  def readProcessFromArg(arg: String): EspProcess = {
    val canonicalJson = if (arg.startsWith("@")) {
      scala.io.Source.fromFile(arg.substring(1)).mkString
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

  def prepareServiceWithEnabledInvocationCollector(runId: TestRunId, service: ObjectWithMethodDef): ObjectWithMethodDef = {
    new OverriddenObjectWithMethodDef(service) {
      override def invokeMethod(parameterCreator: Map[String, Any], outputVariableNameOpt: Option[String], additional: Seq[AnyRef]): Any = {
        val newAdditional = additional.map {
          case c: ServiceInvocationCollector => c.enable(runId)
          case a => a
        }
        service.invokeMethod(parameterCreator, outputVariableNameOpt, newAdditional)
      }
    }
  }
}

class StandaloneProcessManagerProvider extends ProcessManagerProvider {

  override def createProcessManager(modelData: ModelData, config: Config): ProcessManager =
    StandaloneProcessManager(modelData, config)

  override def createQueryableClient(config: Config): Option[QueryableClient] = None

  override def name: String = "requestResponseStandalone"

  override def emptyProcessMetadata(isSubprocess: Boolean): TypeSpecificData = StandaloneMetaData(None)

  override def supportsSignals: Boolean = false
}

object StandaloneProcessManagerProvider {

  import net.ceedubs.ficus.readers.ArbitraryTypeReader._
  import pl.touk.nussknacker.engine.util.config.CustomFicusInstances._

  def defaultTypeConfig(config: Config): ProcessingTypeConfig = {
    ProcessingTypeConfig("requestResponseStandalone",
                    config.as[ClasspathConfig]("standaloneConfig").urls,
                    config.getConfig("standaloneConfig"),
                    config.getConfig("standaloneProcessConfig"))
  }

}
