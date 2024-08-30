package pl.touk.nussknacker.engine.process.compiler

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.connector.source.Boundedness
import pl.touk.nussknacker.engine.ModelData
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.api.{MetaData, NodeId, ProcessListener}
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.definition.component.ComponentDefinitionWithImplementation
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.api.process.{
  CustomizableContextInitializerSource,
  FlinkSource,
  FlinkSourceTestSupport
}
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EmptySource}
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, TestDataPreparer}

object TestFlinkProcessCompilerDataFactory {

  def apply(
      process: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      modelData: ModelData,
      collectingListener: ResultsCollectingListener[_]
  ): FlinkProcessCompilerDataFactory = {
    new StubbedFlinkProcessCompilerDataFactory(
      process,
      modelData.configCreator,
      modelData.extractModelDefinitionFun,
      modelData.modelConfig,
      modelData.namingStrategy,
      ComponentUseCase.TestRuntime
    ) {

      override protected def adjustListeners(
          defaults: List[ProcessListener],
          modelDependencies: ProcessObjectDependencies
      ): List[ProcessListener] = {
        collectingListener :: defaults
      }

      override protected def prepareSourceFactory(
          sourceFactory: ComponentDefinitionWithImplementation,
          context: ComponentDefinitionContext
      ): ComponentDefinitionWithImplementation = {
        sourceFactory.withImplementationInvoker(new StubbedComponentImplementationInvoker(sourceFactory) {
          private lazy val sourcePreparer = new StubbedSourcePreparer(
            new TestDataPreparer(
              context.userCodeClassLoader,
              context.expressionConfig,
              context.dictRegistry,
              context.classDefinitions,
              process.metaData
            ),
            scenarioTestData
          )

          override def handleInvoke(
              originalSource: Any,
              typingResult: TypingResult,
              nodeId: NodeId
          ): Any = {
            originalSource match {
              case sourceWithTestSupport: Source with FlinkSourceTestSupport[Object @unchecked] =>
                sourcePreparer.prepareStubbedSource(sourceWithTestSupport, typingResult, nodeId)
              case _ =>
//              TODO: Why not throw exception here? Maybe we need to remodel FlinkSourceWithParameters interface?
                EmptySource(typingResult)
            }
          }
        })
      }

      override protected def prepareService(
          service: ComponentDefinitionWithImplementation,
          context: ComponentDefinitionContext
      ): ComponentDefinitionWithImplementation = service

      override protected def exceptionHandler(
          metaData: MetaData,
          modelDependencies: ProcessObjectDependencies,
          listeners: Seq[ProcessListener],
          classLoader: ClassLoader
      ): FlinkExceptionHandler = {
        new TestFlinkExceptionHandler(metaData, modelDependencies, listeners, classLoader)
      }

    }
  }

}

class StubbedSourcePreparer(
    testDataPreparer: TestDataPreparer,
    scenarioTestData: ScenarioTestData
) {

  def prepareStubbedSource(
      originalSource: Source with FlinkSourceTestSupport[Object],
      typingResult: TypingResult,
      nodeId: NodeId
  ): FlinkSource = {
    val samples: List[Object] = collectSamples(originalSource, nodeId)
    originalSource match {
      case sourceWithContextInitializer: CustomizableContextInitializerSource[Object @unchecked] =>
        new CollectionSource[Object](
          list = samples,
          timestampAssigner = originalSource.timestampAssignerForTest,
          returnType = typingResult,
          boundedness = Boundedness.BOUNDED
        ) {
          override val contextInitializer: ContextInitializer[Object] = sourceWithContextInitializer.contextInitializer
        }
      case _ =>
        new CollectionSource[Object](
          list = samples,
          timestampAssigner = originalSource.timestampAssignerForTest,
          returnType = typingResult,
          boundedness = Boundedness.BOUNDED
        )
    }
  }

  private def collectSamples(originalSource: Source, nodeId: NodeId): List[Object] = {
    val testRecordsForSource = scenarioTestData.testRecords.filter(_.sourceId == nodeId)
    testDataPreparer.prepareRecordsForTest(originalSource, testRecordsForSource)
  }

}

class TestFlinkExceptionHandler(
    metaData: MetaData,
    modelDependencies: ProcessObjectDependencies,
    listeners: Seq[ProcessListener],
    classLoader: ClassLoader
) extends FlinkExceptionHandler(metaData, modelDependencies, listeners, classLoader) {
  @silent("deprecated")
  override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

  override val consumer: FlinkEspExceptionConsumer = _ => {}

}
