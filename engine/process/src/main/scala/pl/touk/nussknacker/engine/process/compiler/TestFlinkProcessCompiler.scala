package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.scala.{ConnectedStreams, DataStream}
import org.apache.flink.streaming.util.serialization.DeserializationSchema
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.deployment.test.TestData
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.api.process.ProcessConfigCreator
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.{ResultsCollectingListener, TestRunId}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.definition.ProcessDefinitionExtractor
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkSourceFactory, SignalSenderKey}
import pl.touk.nussknacker.engine.flink.api.signal.FlinkProcessSignalSender
import pl.touk.nussknacker.engine.flink.util.exception.ConsumingNonTransientExceptions
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.graph.node.Source

class TestFlinkProcessCompiler(creator: ProcessConfigCreator,
                               config: Config,
                               collectingListener: ResultsCollectingListener,
                               process: EspProcess,
                               testData: TestData, executionConfig: ExecutionConfig) extends StubbedFlinkProcessCompiler(process, creator, config) {

  import pl.touk.nussknacker.engine.util.Implicits._


  override protected def listeners(): Seq[ProcessListener] = List(collectingListener) ++ super.listeners()

  override protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef): Option[ObjectWithMethodDef] = {
    val originalSource = sourceFactory.obj.asInstanceOf[FlinkSourceFactory[Object]]
    implicit val typeInfo = originalSource.typeInformation
    originalSource.testDataParser.map { testDataParser =>
      val testObjects = testDataParser.parseTestData(testData.testData)
      val testFactory = CollectionSource[Object](executionConfig, testObjects, originalSource.timestampAssigner)
      overrideObjectWithMethod(sourceFactory, (_, _) => testFactory)
    }
  }

  override protected def prepareService(service: ObjectWithMethodDef): ObjectWithMethodDef = {
    overrideObjectWithMethod(service, (parameterCreator: (String => Option[AnyRef]), additional: Seq[AnyRef]) => {
      val newAdditional = additional.map {
        case c: ServiceInvocationCollector => c.enable(collectingListener.runId)
        case a => a
      }
      service.invokeMethod(parameterCreator, newAdditional)
    })
  }

  //exceptions are recorded any way, by listeners
  override protected def prepareExceptionHandler(exceptionHandler: ObjectWithMethodDef): ObjectWithMethodDef = {
    overrideObjectWithMethod(exceptionHandler, (_, _) =>
      new FlinkEspExceptionHandler with ConsumingNonTransientExceptions {
        override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

        override protected def consumer: FlinkEspExceptionConsumer = new FlinkEspExceptionConsumer {
          override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit = {}
        }
      }
    )
  }

}


