package pl.touk.nussknacker.engine.process.compiler

import com.typesafe.config.Config
import org.apache.flink.api.common.ExecutionConfig
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import pl.touk.nussknacker.engine.api.ProcessListener
import pl.touk.nussknacker.engine.api.deployment.TestProcess.TestData
import pl.touk.nussknacker.engine.api.exception.{EspExceptionInfo, NonTransientException}
import pl.touk.nussknacker.engine.api.namespaces.ObjectNaming
import pl.touk.nussknacker.engine.api.process.{ProcessConfigCreator, ProcessObjectDependencies}
import pl.touk.nussknacker.engine.definition.DefinitionExtractor.ObjectWithMethodDef
import pl.touk.nussknacker.engine.flink.api.exception.{FlinkEspExceptionConsumer, FlinkEspExceptionHandler}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkContextInitializer, FlinkIntermediateRawSource, FlinkSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.util.exception.ConsumingNonTransientExceptions
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.graph.EspProcess
import pl.touk.nussknacker.engine.modelconfig.{InputConfigDuringExecution, ModelConfigLoader}
import pl.touk.nussknacker.engine.testmode.ResultsCollectingListener

class TestFlinkProcessCompiler(creator: ProcessConfigCreator,
                               inputConfigDuringExecution: Config,
                               collectingListener: ResultsCollectingListener,
                               process: EspProcess,
                               testData: TestData, executionConfig: ExecutionConfig,
                               objectNaming: ObjectNaming) extends StubbedFlinkProcessCompiler(process, creator, inputConfigDuringExecution, objectNaming) {


  override protected def listeners(processObjectDependencies: ProcessObjectDependencies): Seq[ProcessListener] =
    List(collectingListener) ++ super.listeners(processObjectDependencies)

  override protected def prepareSourceFactory(sourceFactory: ObjectWithMethodDef): ObjectWithMethodDef = {
    overrideObjectWithMethod(sourceFactory, (paramFun, outputVariableNameOpt, additional, returnType) => {
      val originalSource = sourceFactory.invokeMethod(paramFun, outputVariableNameOpt, additional).asInstanceOf[FlinkSource[Object]]
      originalSource match {
        case sourceWithTestSupport: FlinkSourceTestSupport[Object@unchecked] =>
          val testObjects = sourceWithTestSupport.testDataParser.parseTestData(testData.testData)
          sourceWithTestSupport match {
            case providerWithTransformation: FlinkIntermediateRawSource[Object@unchecked] =>
              new CollectionSource[Object](executionConfig, testObjects, sourceWithTestSupport.timestampAssignerForTest, returnType())(sourceWithTestSupport.typeInformation) {
                override val contextInitializer: FlinkContextInitializer[Object] = providerWithTransformation.contextInitializer
              }
            case _ =>
              new CollectionSource[Object](executionConfig, testObjects, sourceWithTestSupport.timestampAssignerForTest, returnType())(sourceWithTestSupport.typeInformation)
          }
        case _ =>
          throw new IllegalArgumentException(s"Source ${originalSource.getClass} cannot be stubbed - it does'n provide test data parser")
      }
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

  override protected def prepareService(service: ObjectWithMethodDef): ObjectWithMethodDef = service
}



