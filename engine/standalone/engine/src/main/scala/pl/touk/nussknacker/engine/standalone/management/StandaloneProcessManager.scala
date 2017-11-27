package pl.touk.nussknacker.engine.standalone.management

import java.util.concurrent.TimeUnit

import cats.data.Validated.{Invalid, Valid}
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import com.typesafe.scalalogging.LazyLogging
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.EndingReference
import pl.touk.nussknacker.engine.api.deployment._
import pl.touk.nussknacker.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.nussknacker.engine.api.process.SourceFactory
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.{ServiceInvocationCollector, SinkInvocationCollector}
import pl.touk.nussknacker.engine.api.test.{ResultsCollectingListener, ResultsCollectingListenerHolder, TestRunId}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.canonize.ProcessCanonizer
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor.ProcessDefinition
import pl.touk.nussknacker.engine.definition._
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.Source
import pl.touk.nussknacker.engine.marshall.ProcessMarshaller
import pl.touk.nussknacker.engine.standalone.api.DeploymentData
import pl.touk.nussknacker.engine.standalone.{StandaloneModelData, StandaloneProcessInterpreter}
import pl.touk.nussknacker.engine.standalone.utils.StandaloneContextPreparer
import pl.touk.nussknacker.engine.standalone.api.types._

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}


object StandaloneProcessManager {
  def apply(modelData: ModelData, config: Config) : StandaloneProcessManager = new StandaloneProcessManager(modelData, StandaloneProcessClient(config))
}

class StandaloneProcessManager(modelData: ModelData, client: StandaloneProcessClient)
  extends ProcessManager with LazyLogging {

  private implicit val ec = ExecutionContext.Implicits.global

  import argonaut.ArgonautShapeless._

  override def deploy(processId: String, processDeploymentData: ProcessDeploymentData,
                      savepointPath: Option[String]): Future[Unit] = {
    savepointPath match {
      case Some(_) => Future.failed(new UnsupportedOperationException("Cannot make savepoint on standalone process"))
      case None =>
        processDeploymentData match {
          case GraphProcess(processAsJson) =>
            client.deploy(DeploymentData(processId, processAsJson, System.currentTimeMillis()))
          case CustomProcess(mainClass) =>
            Future.failed(new UnsupportedOperationException("custom process in standalone engine is not supported"))
        }
    }

  }

  override def savepoint(processId: String, savepointDir: String): Future[String] = {
    Future.failed(new UnsupportedOperationException("Cannot make savepoint on standalone process"))
  }

  override def test(processId: String, processJson: String, testData: TestData): Future[TestResults] = {
    Future{
      StandaloneTestMain.run(processJson, testData, modelData)
    }
  }

  override def findJobStatus(name: String): Future[Option[ProcessState]] = {
    client.findStatus(name)
  }

  override def cancel(name: String): Future[Unit] = {
    client.cancel(name)
  }

}

object StandaloneTestMain {

  def run(processJson: String, testData: TestData, modelData: ModelData): TestResults = {
    new StandaloneTestMain(
      testData = testData,
      process = TestUtils.readProcessFromArg(processJson),
      modelData).runTest()
  }

}

class StandaloneTestMain(testData: TestData, process: EspProcess, modelData: ModelData) {

  private val timeout = FiniteDuration(10, TimeUnit.SECONDS)

  import ExecutionContext.Implicits.global

  def runTest(): TestResults = {
    val creator = modelData.configCreator
    val config = modelData.processConfig

    val definitions = ProcessDefinitionExtractor.extractObjectWithMethods(creator, config)
    val parsedTestData = readTestData(definitions)

    val collectingListener = ResultsCollectingListenerHolder.registerRun

    //in tests we don't send metrics anywhere
    val testContext = new StandaloneContextPreparer(new MetricRegistry)

    //FIXME: validation??
    val standaloneInterpreter = StandaloneProcessInterpreter(process, testContext, modelData,
      definitionsPostProcessor = prepareMocksForTest(collectingListener),
      additionalListeners = List(collectingListener)
    ).toOption.get

    try {
      val results = Await.result(Future.sequence(parsedTestData.map(standaloneInterpreter.invokeToResult)), timeout)
      collectSinkResults(collectingListener.runId, results)
      collectExceptions(collectingListener, results)
      collectingListener.results
    } finally {
      collectingListener.clean()
    }

  }

  private def collectSinkResults(runId: TestRunId, results: List[InterpretationResultType]) = {
    val successfulResults = results.flatMap(_.right.toOption.toList.flatten)
    successfulResults.foreach { result =>
      val node = result.reference.asInstanceOf[EndingReference].nodeId
      SinkInvocationCollector(runId, node, node, _.toString).collect(result)
    }
  }

  private def collectExceptions(listener: ResultsCollectingListener, results: List[InterpretationResultType]) = {
    val exceptions = results.flatMap(_.left.toOption)
    exceptions.flatMap(_.toList).foreach(listener.exceptionThrown)
  }


  private def readTestData(definitions: ProcessDefinition[ObjectWithMethodDef]): List[Any] = {
    //FIXME: asInstanceOf, should be proper handling of SubprocessInputDefinition
    val sourceType = process.root.data.asInstanceOf[Source].ref.typ
    val objectWithMethodDef = definitions.sourceFactories(sourceType)
    val originalSource = objectWithMethodDef.obj.asInstanceOf[SourceFactory[Any]]
    val parsedTestData = originalSource.testDataParser.map { testDataParser =>
      testDataParser.parseTestData(testData.testData)
    }.getOrElse(throw new IllegalArgumentException(s"Source $sourceType cannot be tested"))
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
  private val ProcessMarshaller = new ProcessMarshaller

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
    new ObjectWithMethodDef(service.obj, service.methodDef, service.objectDefinition) {
      override def invokeMethod(parameterCreator: String => Option[AnyRef], additional: Seq[AnyRef]): Any = {
        val newAdditional = additional.map {
          case c: ServiceInvocationCollector => c.enable(runId)
          case a => a
        }
        service.invokeMethod(parameterCreator, newAdditional)
      }
    }
  }

}