package pl.touk.esp.engine.process.runner

import java.net.URL

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.restartstrategy.RestartStrategies.NoRestartStrategyConfiguration
import org.apache.flink.configuration.{ConfigConstants, Configuration}
import org.apache.flink.runtime.minicluster.LocalFlinkMiniCluster
import org.apache.flink.streaming.api.scala.StreamExecutionEnvironment
import pl.touk.esp.engine.api.deployment.test.{TestData, TestResults}
import pl.touk.esp.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.esp.engine.api.test.{ResultsCollectingListener, ResultsCollectingListenerHolder, TestRunId}
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor
import pl.touk.esp.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.esp.engine.flink.api.process.FlinkSourceFactory
import pl.touk.esp.engine.flink.util.exception.{ConsumingNonTransientExceptions, DefaultEspExceptionHandler}
import pl.touk.esp.engine.flink.util.source.CollectionSource
import pl.touk.esp.engine.graph.EspProcess
import pl.touk.esp.engine.process.FlinkProcessRegistrar
import pl.touk.esp.engine.process.util.MetaDataExtractor

import scala.collection.JavaConverters._


object FlinkTestMain extends FlinkRunner {

  def run(processJson: String, config: Config, testData: TestData, urls: List[URL]): TestResults = {
    val process = readProcessFromArg(processJson)
    val creator: ProcessConfigCreator = loadCreator(config)

    new FlinkTestMain(config, testData, process, creator).runTest(urls)
  }
}

class FlinkTestMain(config: Config, testData: TestData, process: EspProcess, creator: ProcessConfigCreator) extends Serializable {

  import pl.touk.esp.engine.util.Implicits._

  def overWriteRestartStrategy(env: StreamExecutionEnvironment) = env.setRestartStrategy(new NoRestartStrategyConfiguration)

  def runTest(urls: List[URL]): TestResults = {
    val env = StreamExecutionEnvironment.createLocalEnvironment(MetaDataExtractor.extractStreamMetaDataOrFail(process.metaData).parallelism.getOrElse(1))
    val collectingListener = ResultsCollectingListenerHolder.registerRun
    try {
      val registrar: FlinkProcessRegistrar = FlinkProcessRegistrar(creator, config, prepareMocksForTest(env.getConfig, collectingListener), List(collectingListener))
      registrar.register(env, process, Option(collectingListener.runId))
      overWriteRestartStrategy(env)
      execute(env, urls)
      collectingListener.results
    } finally {
      collectingListener.clean()
    }
  }

  private def prepareMocksForTest(executionConfig: ExecutionConfig, listener: ResultsCollectingListener)(definitions: ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef]):
    ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef] = {

    val sourceType = process.root.data.ref.typ
    val testSource = definitions.sourceFactories.get(sourceType)
      .flatMap(prepareTestDataSourceFactory(executionConfig))
      .getOrElse(throw new IllegalArgumentException(s"Source $sourceType cannot be tested"))

    val servicesWithEnabledInvocationCollector = definitions.services.mapValuesNow { service =>
      prepareServiceWithEnabledInvocationCollector(listener.runId, service)
    }
    definitions
      .copy(sourceFactories = definitions.sourceFactories + (sourceType -> testSource),
            services = servicesWithEnabledInvocationCollector,
            exceptionHandlerFactory = prepareDummyExceptionHandler(definitions.exceptionHandlerFactory)
      )
  }

  private def prepareTestDataSourceFactory(executionConfig: ExecutionConfig)(objectWithMethodDef: ObjectWithMethodDef): Option[ObjectWithMethodDef] = {
    val originalSource = objectWithMethodDef.obj.asInstanceOf[FlinkSourceFactory[Object]]
    implicit val typeInfo = originalSource.typeInformation
    originalSource.testDataParser.map { testDataParser =>
      val testObjects = testDataParser.parseTestData(testData.testData)
      val testFactory = CollectionSource[Object](executionConfig, testObjects, None)
      new TestDataInvokingObjectWithMethodDef(testFactory, objectWithMethodDef)
    }
  }

  private def prepareServiceWithEnabledInvocationCollector(runId: TestRunId, service: ObjectWithMethodDef): ObjectWithMethodDef = {
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

  //exceptions are recorded any way, by listeners
  private def prepareDummyExceptionHandler(exceptionHandler: ObjectWithMethodDef) : ObjectWithMethodDef = {
    new ObjectWithMethodDef(exceptionHandler.obj, exceptionHandler.methodDef, exceptionHandler.objectDefinition) {
      override def invokeMethod(parameterCreator: String => Option[AnyRef], additional: Seq[AnyRef]): Any = {
        new FlinkEspExceptionHandler with ConsumingNonTransientExceptions{
          override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

          override protected def consumer: FlinkEspExceptionConsumer = new FlinkEspExceptionConsumer {
            override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit = {}
          }
        }
      }
    }
  }

  //we use own LocalFlinkMiniCluster, instead of LocalExecutionEnvironment, to be able to pass own classpath...
  private def execute(env: StreamExecutionEnvironment, urls: List[URL]) = {
    val streamGraph = env.getStreamGraph
    streamGraph.setJobName(process.id)

    val jobGraph = streamGraph.getJobGraph
    jobGraph.setClasspaths(urls.asJava)

    val configuration: Configuration = new Configuration
    configuration.addAll(jobGraph.getJobConfiguration)
    configuration.setInteger(ConfigConstants.TASK_MANAGER_NUM_TASK_SLOTS, env.getParallelism)

    val exec: LocalFlinkMiniCluster = new LocalFlinkMiniCluster(configuration, true)
    try {
      exec.start()
      exec.submitJobAndWait(jobGraph, printUpdates = false)
    } finally {
      exec.stop()
    }
  }

}

//TODO: no to jest dosc paskudne, ale na razie nie mam pomyslu jak to ladnie zrobic...
private class TestDataInvokingObjectWithMethodDef(testFactory: AnyRef, original: ObjectWithMethodDef)
  extends ObjectWithMethodDef(testFactory, original.methodDef, original.objectDefinition) {

  override def invokeMethod(paramFun: String => Option[AnyRef], additional: Seq[AnyRef]) = testFactory

}
