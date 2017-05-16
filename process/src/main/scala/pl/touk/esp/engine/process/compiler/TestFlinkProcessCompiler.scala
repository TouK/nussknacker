package pl.touk.esp.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.{ConnectedStreams, DataStream}
import org.apache.flink.streaming.util.serialization.DeserializationSchema
import pl.touk.esp.engine.api.ProcessListener
import pl.touk.esp.engine.api.deployment.test.TestData
import pl.touk.esp.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.esp.engine.api.process.ProcessConfigCreator
import pl.touk.esp.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.esp.engine.api.test.{ResultsCollectingListener, TestRunId}
import pl.touk.esp.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.esp.engine.definition.ProcessDefinitionExtractor
import pl.touk.esp.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.esp.engine.flink.api.process.{FlinkSourceFactory, SignalSenderKey}
import pl.touk.esp.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.esp.engine.flink.util.exception.ConsumingNonTransientExceptions
import pl.touk.esp.engine.flink.util.source.CollectionSource
import pl.touk.esp.engine.graph.EspProcess

class TestFlinkProcessCompiler(creator: ProcessConfigCreator,
                               config: Config,
                               collectingListener: ResultsCollectingListener,
                               process: EspProcess,
                               testData: TestData, executionConfig: ExecutionConfig) extends FlinkProcessCompiler(creator, config) {

  import pl.touk.esp.engine.util.Implicits._


  override protected def listeners(): Seq[ProcessListener] = List(collectingListener) ++ super.listeners()

  override protected def signalSenders: Map[SignalSenderKey, FlinkProcessSignalSender] =
    super.signalSenders.mapValuesNow(_ => DummyFlinkSignalSender)

  override protected def definitions(): ProcessDefinitionExtractor.ProcessDefinition[ObjectWithMethodDef] = {
    val createdDefinitions = super.definitions()

    val sourceType = process.root.data.ref.typ
    val testSource = createdDefinitions.sourceFactories.get(sourceType)
      .flatMap(prepareTestDataSourceFactory(executionConfig))
      .getOrElse(throw new IllegalArgumentException(s"Source $sourceType cannot be tested"))

    val servicesWithEnabledInvocationCollector = createdDefinitions.services.mapValuesNow { service =>
      prepareServiceWithEnabledInvocationCollector(collectingListener.runId, service)
    }
    createdDefinitions
      .copy(sourceFactories = createdDefinitions.sourceFactories + (sourceType -> testSource),
            services = servicesWithEnabledInvocationCollector,
            exceptionHandlerFactory = prepareDummyExceptionHandler(createdDefinitions.exceptionHandlerFactory)
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

}

//TODO: well, this is pretty disgusting, but currently don't have idea how to improve it...
private class TestDataInvokingObjectWithMethodDef(testFactory: AnyRef, original: ObjectWithMethodDef)
  extends ObjectWithMethodDef(testFactory, original.methodDef, original.objectDefinition) {

  override def invokeMethod(paramFun: String => Option[AnyRef], additional: Seq[AnyRef]) = testFactory

}

private object DummyFlinkSignalSender extends FlinkProcessSignalSender {
  override def connectWithSignals[InputType, SignalType: TypeInformation](start: DataStream[InputType], processId: String, nodeId: String, schema: DeserializationSchema[SignalType]): ConnectedStreams[InputType, SignalType] = {
    start.connect(start.executionEnvironment.fromElements[SignalType]())
  }
}

