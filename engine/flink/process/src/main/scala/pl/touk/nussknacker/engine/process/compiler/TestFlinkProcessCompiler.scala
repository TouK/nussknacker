package pl.touk.nussknacker.engine.process.compiler

import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessObjectDependencies, TestDataParserProvider}
import pl.touk.nussknacker.engine.api.test.InvocationCollectors.ServiceInvocationCollector
import pl.touk.nussknacker.engine.api.test.ResultsCollectingListener
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.api.process.FlinkSource
import pl.touk.nussknacker.engine.flink.util.exception.ConsumingNonTransientExceptions
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.modelconfig.{InputConfigDuringExecution, ModelConfigLoader}

class TestFlinkProcessCompiler(creator: ProcessConfigCreator,
                               inputConfigDuringExecution: InputConfigDuringExecution,
                               modelConfigLoader: ModelConfigLoader,
                               collectingListener: ResultsCollectingListener,
                               process: EspProcess,
                               testData: TestData, executionConfig: ExecutionConfig,
                               objectNaming: ObjectNaming) extends StubbedFlinkProcessCompiler(process, creator, inputConfigDuringExecution, modelConfigLoader, objectNaming) {


  override protected def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    List(collectingListener) ++ super.listeners(processObjectDependencies)

  override protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef): ObjectWithMethodDef = {
    overrideObjectWithMethod(sourceFactory, (paramFun, outputVariableNameOpt, additional, returnType) => {
      val originalSource = sourceFactory.invokeMethod(paramFun, outputVariableNameOpt, additional).asInstanceOf[FlinkSource[Object]]
      originalSource match {
        case testDataParserProvider: TestDataParserProvider[Object@unchecked] =>
          val testObjects = testDataParserProvider.testDataParser.parseTestData(testData.testData)
          CollectionSource[Object](executionConfig, testObjects, originalSource.timestampAssignerForTest, returnType())(originalSource.typeInformation)
        case _ =>
          throw new IllegalArgumentException(s"Source ${originalSource.getClass} cannot be stubbed - it does'n provide test data parser")
      }
    })
  }

  override protected def prepareService(service: ObjectWithMethodDef): ObjectWithMethodDef = {
    overrideObjectWithMethod(service, (parameterCreator: Map[String, Any], outputVariableNameOpt, additional: Seq[AnyRef], _) => {
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


