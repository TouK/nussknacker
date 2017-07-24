package pl.touk.esp.engine.standalone.management

import java.io.File
import java.lang.reflect.InvocationTargetException
import java.net.{URL, URLClassLoader}
import java.util.UUID
import java.util.concurrent.TimeUnit

import cats.data.Validated.{Invalid, Valid}
import com.codahale.metrics.MetricRegistry
import com.typesafe.config.Config
import dispatch.Http
import pl.touk.esp.engine.api.EndingReference
import pl.touk.esp.engine.api.deployment._
import pl.touk.esp.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.esp.engine.api.process.{ProcessConfigCreator, SourceFactory}
import pl.touk.esp.engine.api.test.InvocationCollectors.{ServiceInvocationCollector, SinkInvocationCollector}
import pl.touk.esp.engine.api.test.{ResultsCollectingListener, ResultsCollectingListenerHolder, TestRunId}
import pl.touk.esp.engine.canonicalgraph.CanonicalProcess
import pl.touk.esp.engine.canonize.ProcessCanonizer
import pl.touk.esp.engine.definition.DefinitionExtractor.{ObjectDefinition, ObjectWithMethodDef}
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor.{ObjectProcessDefinition, ProcessDefinition}
import pl.touk.esp.engine.definition._
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.graph.node.Source
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.standalone.StandaloneProcessInterpreter
import pl.touk.esp.engine.standalone.utils.{StandaloneContext, StandaloneContextPreparer}
import pl.touk.esp.engine.util.ThreadUtils
import pl.touk.esp.engine.util.loader.JarClassLoader
import pl.touk.esp.engine.util.service.{AuditDispatchClient, LogCorrelationId}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

class StandaloneProcessManager(config: Config)
  extends ProcessManager with ConfigCreatorTestInfoProvider with ProcessDefinitionProvider with ConfigCreatorSignalDispatcher {


  implicit val ec = ExecutionContext.Implicits.global

  private val standaloneConf = config.getConfig("standaloneConfig")

  private val jarClassLoader = JarClassLoader(standaloneConf.getString("jarPath"))

  private val httpClient = Http.apply()
  private val dispatchClient = new AuditDispatchClient {
    override protected def http = httpClient
  }
  private val managementUrl = standaloneConf.getString("managementUrl")

  private val processConfigPart = {
    val configName = standaloneConf.getString("processConfig")
    config.getConfig(configName).root()
  }

  val processConfig = processConfigPart.toConfig

  private val testRunner = StandaloneTestRunner(processConfig, jarClassLoader.jarUrl)

  import argonaut.ArgonautShapeless._

  override def deploy(processId: String, processDeploymentData: ProcessDeploymentData,
                      savepointPath: Option[String]): Future[Unit] = {
    savepointPath match {
      case Some(_) => Future.failed(new UnsupportedOperationException("Cannot make savepoint on standalone process"))
      case None =>
        implicit val correlationId = LogCorrelationId(UUID.randomUUID().toString)
        val deployUrl = dispatch.url(managementUrl) / "deploy"
        processDeploymentData match {
          case GraphProcess(processAsJson) =>
            val data = DeploymentData(processId, processAsJson)
            dispatchClient.postObjectAsJsonWithoutResponseParsing(deployUrl, data).map(_ => ())
          case CustomProcess(mainClass) =>
            Future.failed(new UnsupportedOperationException("custom process in standalone engine is not supported"))
        }
    }
        
  }


  override def savepoint(processId: String, savepointDir: String): Future[String] = {
    Future.failed(new UnsupportedOperationException("Cannot make savepoint on standalone process"))
  }

  override def test(processId: String, processJson: String, testData: TestData): Future[TestResults] = {
    Future(testRunner.test(processId, processJson, testData))
  }

  override def findJobStatus(name: String): Future[Option[ProcessState]] = {
    implicit val correlationId = LogCorrelationId(UUID.randomUUID().toString)
    val jobStatusUrl = dispatch.url(managementUrl) / "checkStatus" / name
    dispatchClient.getPossiblyUnavailableJsonAsObject[ProcessState](jobStatusUrl)
  }

  override def cancel(name: String): Future[Unit] = {
    implicit val correlationId = LogCorrelationId(UUID.randomUUID().toString)
    val cancelUrl = dispatch.url(managementUrl) / "cancel" / name
    dispatchClient.sendWithAuditAndStatusChecking(cancelUrl.POST).map(_ => ())
  }

  lazy val configCreator: ProcessConfigCreator =
    jarClassLoader.createProcessConfigCreator(processConfig.getString("processConfigCreatorClass"))

  override def getProcessDefinition: ProcessDefinition[ObjectDefinition] = {
    ThreadUtils.withThisAsContextClassLoader(jarClassLoader.classLoader) {
      ObjectProcessDefinition(ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, processConfig))
    }
  }

  lazy val buildInfo: Map[String, String] = configCreator.buildInfo()
}


object StandaloneTestRunner {
  def apply(config: Config, jarUrl: URL): TestUtils.StandaloneTestRunner = {
    new TestUtils.StandaloneTestRunner(config, jarUrl)
  }

}

object StandaloneTestMain {

  def run(processJson: String, config: Config, testData: TestData, urls: List[URL]): TestResults = {
    val process = TestUtils.readProcessFromArg(processJson)
    val creator: ProcessConfigCreator = loadCreator(config)

    new StandaloneTestMain(config, testData, process, creator).runTest()
  }

  protected def loadCreator(config: Config): ProcessConfigCreator =
    ThreadUtils.loadUsingContextLoader(config.getString("processConfigCreatorClass")).newInstance().asInstanceOf[ProcessConfigCreator]

}

class StandaloneTestMain(config: Config, testData: TestData, process: EspProcess, creator: ProcessConfigCreator) {

  private val timeout = FiniteDuration(10, TimeUnit.SECONDS)

  import ExecutionContext.Implicits.global

  def runTest(): TestResults = {
    val definitions = ProcessDefinitionExtractor.extractObjectWithMethods(creator, config)
    val parsedTestData = readTestData(definitions)

    val collectingListener = ResultsCollectingListenerHolder.registerRun

    //in tests we don't send metrics anywhere
    val testContext = new StandaloneContextPreparer(new MetricRegistry)

    //FIXME: validation??
    val standaloneInterpreter = StandaloneProcessInterpreter(process, testContext, creator, config,
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

  private def collectSinkResults(runId: TestRunId, results: List[StandaloneProcessInterpreter.InterpretationResultType]) = {
    val successfulResults = results.flatMap(_.right.toOption.toList.flatten)
    successfulResults.foreach { result =>
      val node = result.reference.asInstanceOf[EndingReference].nodeId
      SinkInvocationCollector(runId, node, node, _.toString).collect(result)
    }
  }

  private def collectExceptions(listener: ResultsCollectingListener, results: List[StandaloneProcessInterpreter.InterpretationResultType]) = {
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
    import pl.touk.esp.engine.util.Implicits._
    val servicesWithEnabledInvocationCollector = definitions.services.mapValuesNow { service =>
      TestUtils.prepareServiceWithEnabledInvocationCollector(listener.runId, service)
    }
    definitions
      .copy(services = servicesWithEnabledInvocationCollector)
  }

}

//FIXME deduplicate with pl.touk.esp.engine.process.runner.FlinkRunner?
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

  class StandaloneTestRunner(config: Config, jar: URL) {
    import scala.reflect.runtime.{universe => ru}

    val jarClassLoader = JarClassLoader(jar)

    private val invoker: ru.MethodMirror = {
      val m = ru.runtimeMirror(jarClassLoader.classLoader)
      val module = m.staticModule("pl.touk.esp.engine.standalone.management.StandaloneTestMain")
      val im = m.reflectModule(module)
      val method = im.symbol.info.decl(ru.TermName("run")).asMethod
      val objMirror = m.reflect(im.instance)
      objMirror.reflectMethod(method)
    }

    def test(processId: String, processJson: String, testData: TestData): TestResults = {
      //we have to use context loader, as in UI we have don't have nussknacker-process on classpath...
      ThreadUtils.withThisAsContextClassLoader(jarClassLoader.classLoader) {
        tryToInvoke(testData, processJson).asInstanceOf[TestResults]
      }
    }

    def tryToInvoke(testData: TestData, json: String): Any = try {
      invoker(json, config, testData, List(jar))
    } catch {
      case e:InvocationTargetException => throw e.getTargetException
    }

  }

}