package pl.touk.nussknacker.engine.process.compiler

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.restartstrategy.RestartStrategies
import org.apache.flink.api.connector.source.Boundedness
import pl.touk.nussknacker.engine.{ModelConfig, ModelData, RuntimeMode}
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.NodesDeploymentData
import pl.touk.nussknacker.engine.api.process._
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.canonicalgraph.CanonicalProcess
import pl.touk.nussknacker.engine.compile.nodecompilation.EvaluableLazyParameterCreator
import pl.touk.nussknacker.engine.definition.component.{ComponentDefinitionWithImplementation, ComponentImplementationInvoker}
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionConsumer
import pl.touk.nussknacker.engine.flink.api.process.{CustomizableContextInitializerSource, FlinkSource, FlinkSourceTestSupport}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.StandardTimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.source.{CollectionSource, EmptySource}
import pl.touk.nussknacker.engine.process.exception.FlinkExceptionHandler
import pl.touk.nussknacker.engine.testmode.{ResultsCollectingListener, TestDataPreparer}

object TestFlinkProcessCompilerDataFactory {

  def apply(
      process: CanonicalProcess,
      scenarioTestData: ScenarioTestData,
      modelData: ModelData,
      jobData: JobData,
      collectingListener: ResultsCollectingListener[_]
  ): FlinkProcessCompilerDataFactory = {
    new StubbedFlinkProcessCompilerDataFactory(
      process,
      modelData.configCreator,
      modelData.extractModelDefinitionFun,
      modelData.modelConfig,
      RuntimeMode.Test,
      modelData.additionalConfigsFromProvider,
      NodesDeploymentData.empty,
      List.empty,
    ) {

      override protected def adjustListeners(
          defaults: List[ProcessListener],
          modelConfig: ModelConfig
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
              jobData
            ),
            scenarioTestData
          )

          override def create(
              original: ComponentImplementationInvoker,
              params: Params,
              outputVariableNameOpt: Option[String],
              additional: Seq[AnyRef]
          ): Any = {
            // Transform EvaluableLazyParameterCreator's into EvaluableLazyParameter's
            // in order to have them available for resolving when executing tests
            val resolvedParams = Params.fromRawValuesMap(params.nameToRawValueMap.map { case (name, value) =>
              name -> resolveParam(value)
            })
            original.invokeMethod(resolvedParams, outputVariableNameOpt, additional)
          }

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

          private def resolveParam(param: Any): Any = param match {
            case lazyParameterCreator: EvaluableLazyParameterCreator[_] =>
              sourcePreparer.resolveParam(lazyParameterCreator)
            case other =>
              other
          }
        })
      }

      override protected def prepareService(
          service: ComponentDefinitionWithImplementation,
          context: ComponentDefinitionContext
      ): ComponentDefinitionWithImplementation = service

      override protected def exceptionHandler(
          metaData: MetaData,
          modelConfig: ModelConfig,
          listeners: Seq[ProcessListener],
          classLoader: ClassLoader
      ): FlinkExceptionHandler = {
        new TestFlinkExceptionHandler(metaData, modelConfig, listeners, classLoader)
      }

    }
  }

}

class StubbedSourcePreparer(
    testDataPreparer: TestDataPreparer,
    scenarioTestData: ScenarioTestData
) {

  def resolveParam[T <: AnyRef](lazyParameterCreator: EvaluableLazyParameterCreator[T]): LazyParameter[T] = {
    testDataPreparer.resolveParam(lazyParameterCreator)
  }

  def prepareStubbedSource(
      originalSource: Source with FlinkSourceTestSupport[Object],
      typingResult: TypingResult,
      nodeId: NodeId
  ): FlinkSource = {
    val samples: List[Object] = collectSamples(originalSource, nodeId)
    val assignerForTestOpt = originalSource.timestampAssignerForTest
    // setting timestamp as currentTimeMillis is good default
    // without this default we would run into issues with timestamp being Long.MIN_VALUE and
    // crashing time windows
    val improvedAssignerForTest = assignerForTestOpt.orElse(
      Some(
        new StandardTimestampWatermarkHandler[Object](
          WatermarkStrategy
            .forMonotonousTimestamps[Object]()
            .withTimestampAssigner(StandardTimestampWatermarkHandler.toAssigner[Object](e => System.currentTimeMillis()))
        )
      )
    )
    originalSource match {
      case sourceWithContextInitializer: CustomizableContextInitializerSource[Object @unchecked] =>
        new CollectionSource[Object](
          list = samples,
          timestampAssigner = improvedAssignerForTest,
          returnType = typingResult,
          boundedness = Boundedness.BOUNDED
        ) {
          override val contextInitializer: ContextInitializer[Object] = sourceWithContextInitializer.contextInitializer
        }
      case _ =>
        new CollectionSource[Object](
          list = samples,
          timestampAssigner = improvedAssignerForTest,
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
    modelConfig: ModelConfig,
    listeners: Seq[ProcessListener],
    classLoader: ClassLoader
) extends FlinkExceptionHandler(metaData, modelConfig, listeners, classLoader) {

  @silent("deprecated")
  override def restartStrategy: RestartStrategies.RestartStrategyConfiguration = RestartStrategies.noRestart()

  override val consumer: FlinkEspExceptionConsumer = _ => {}

}
