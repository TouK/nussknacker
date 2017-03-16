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
import pl.touk.esp.engine.definition.{ConfigCreatorTestInfoProvider, ProcessDefinitionExtractor, ProcessDefinitionProvider}
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.marshall.ProcessMarshaller
import pl.touk.esp.engine.standalone.StandaloneProcessInterpreter
import pl.touk.esp.engine.standalone.utils.{StandaloneContext, StandaloneContextPreparer}
import pl.touk.esp.engine.util.ThreadUtils
import pl.touk.esp.engine.util.service.{AuditDispatchClient, LogCorrelationId}

import scala.concurrent.duration.FiniteDuration
import scala.concurrent.{Await, ExecutionContext, Future}

class StandaloneProcessManager(config: Config)
  extends ProcessManager with ConfigCreatorTestInfoProvider with ProcessDefinitionProvider {


  implicit val ec = ExecutionContext.Implicits.global

  private val standaloneConf = config.getConfig("standaloneConfig")

  private val jarFile = new File(standaloneConf.getString("jarPath"))

  private val classLoader = new URLClassLoader(Array(jarFile.toURI.toURL), getClass.getClassLoader)

  private val httpClient = Http.apply()
  private val dispatchClient = new AuditDispatchClient {
    override protected def http = httpClient
  }
  private val managementUrl = standaloneConf.getString("managementUrl")

  // fixme zdeduplikowac?
  private val processConfigPart = {
    val configName = standaloneConf.getString("processConfig")
    config.getConfig(configName).root()
  }

  val processConfig = processConfigPart.toConfig

  private val testRunner = StandaloneTestRunner(processConfig, jarFile)

  import argonaut.ArgonautShapeless._

  override def deploy(processId: String, processDeploymentData: ProcessDeploymentData): Future[Unit] = {
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


  override def test(processId: String, processDeploymentData: ProcessDeploymentData, testData: TestData): Future[TestResults] = {
    Future(testRunner.test(processId, processDeploymentData, testData))
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
    classLoader.loadClass(processConfig.getString("processConfigCreatorClass")).newInstance().asInstanceOf[ProcessConfigCreator]

  override def getProcessDefinition: ProcessDefinition[ObjectDefinition] = {
    ThreadUtils.withThisAsContextClassLoader(classLoader) {
      ObjectProcessDefinition(ProcessDefinitionExtractor.extractObjectWithMethods(configCreator, processConfig))
    }
  }

  lazy val buildInfo: Map[String, String] = configCreator.buildInfo()
}


object StandaloneTestRunner {
  def apply(config: Config, jarFile: File) = {
    new TestUtils.StandaloneTestRunner(config, List(jarFile.toURI.toURL))
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

    //FIXME: walidacja??
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
    val sourceType = process.root.data.ref.typ
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

//fixme rzeczy do zdeduplikowania
//fixme a moze lepiej testowac po http, a nie lokalnie przez refleksje?
object TestUtils {
  private val ProcessMarshaller = new ProcessMarshaller

  //fixme zdeduplikowac?
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

  //fixme zdeduplikowac
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

  //fixme to chyba cale bedzie mozna wydzielic
  class StandaloneTestRunner(config: Config, jars: List[URL]) {
    import scala.reflect.runtime.{universe => ru}

    val classLoader = new URLClassLoader(jars.toArray, getClass.getClassLoader)

    private val invoker: ru.MethodMirror = {
      val m = ru.runtimeMirror(classLoader)
      val module = m.staticModule("pl.touk.esp.engine.standalone.management.StandaloneTestMain")
      val im = m.reflectModule(module)
      val method = im.symbol.info.decl(ru.TermName("run")).asMethod
      val objMirror = m.reflect(im.instance)
      objMirror.reflectMethod(method)
    }

    def test(processId: String, processDeploymentData: ProcessDeploymentData, testData: TestData): TestResults = {
      //we have to use context loader, as in UI we have don't have esp-process on classpath...
      ThreadUtils.withThisAsContextClassLoader(classLoader) {
        processDeploymentData match {
          case GraphProcess(json) => tryToInvoke(testData, json).asInstanceOf[TestResults]
          case _ => throw new IllegalArgumentException(s"Process $processId with deploymentData $processDeploymentData cannot be tested")
        }
      }
    }

    def tryToInvoke(testData: TestData, json: String): Any = try {
      invoker(json, config, testData, jars)
    } catch {
      case e:InvocationTargetException => throw e.getTargetException
    }

  }

}