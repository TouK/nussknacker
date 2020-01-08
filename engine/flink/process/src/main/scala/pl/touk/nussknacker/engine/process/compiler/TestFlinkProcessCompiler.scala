package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.common.typeinfo.TypeInformation
import pl.touk.nussknacker.engine.{ModelConfigToLoad, ModelData}
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.ResultsCollectingListener
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSourceFactory
import pl.touk.nussknacker.engine.flink.util.exception.ConsumingNonTransientExceptions
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess

class TestFlinkProcessCompiler(creator: ProcessConfigCreator, config: ModelConfigToLoad,
                               collectingListener: ResultsCollectingListener,
                               process: EspProcess,
                               testData: TestData, executionConfig: ExecutionConfig) extends StubbedFlinkProcessCompiler(process, creator, config) {


  override protected def listeners(config: Config): Seq[ProcessListener] = List(collectingListener) ++ super.listeners(config)

  override protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef): ObjectWithMethodDef = {
    val originalSourceFactory = sourceFactory.obj.asInstanceOf[FlinkSourceFactory[Object]]
    implicit val typeInfo: TypeInformation[Object] = originalSourceFactory.typeInformation
    overrideObjectWithMethod(sourceFactory, (paramFun, outputVariableNameOpt, additional, realReturnType) => {
      val originalSource = sourceFactory.invokeMethod(paramFun, outputVariableNameOpt, additional)
      originalSource match {
        case testDataParserProvider: TestDataParserProvider[Object@unchecked] =>
          val testObjects = testDataParserProvider.testDataParser.parseTestData(testData.testData)
          CollectionSource[Object](executionConfig, testObjects, originalSourceFactory.timestampAssigner, realReturnType())
        case _ =>
          throw new IllegalArgumentException(s"Source ${originalSource.getClass} cannot be stubbed - it does'n provide test data parser")
      }
    })
  }

  override protected def prepareService(service: ObjectWithMethodDef): ObjectWithMethodDef = {
    overrideObjectWithMethod(service, (parameterCreator: String => Option[AnyRef], outputVariableNameOpt, additional: Seq[AnyRef], _) => {
      val newAdditional = additional.map {
        case c: ServiceInvocationCollector => c.enable(collectingListener.runId)
        case a => a
      }
      service.invokeMethod(parameterCreator, outputVariableNameOpt, newAdditional)
    })
  }

  //exceptions are recorded any way, by listeners
  override protected def prepareExceptionHandler(exceptionHandler: ObjectWithMethodDef): ObjectWithMethodDef = {
    overrideObjectWithMethod(exceptionHandler, (_, _, _, _) =>
      new FlinkEspExceptionHandler with ConsumingNonTransientExceptions {
        override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

        override protected def consumer: FlinkEspExceptionConsumer = new FlinkEspExceptionConsumer {
          override def consume(exceptionInfo: EspExceptionInfo[NonTransientException]): Unit = {}
        }
      }
    )
  }

}


