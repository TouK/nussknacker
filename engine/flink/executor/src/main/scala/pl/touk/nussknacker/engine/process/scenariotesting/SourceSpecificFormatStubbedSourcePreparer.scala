package pl.touk.nussknacker.engine.process.scenariotesting

import org.apache.flink.api.common.eventtime.{SerializableTimestampAssigner, WatermarkStrategy}
import org.apache.flink.api.connector.source.Boundedness
import pl.touk.nussknacker.engine.api.{LazyParameter, NodeId}
import pl.touk.nussknacker.engine.api.process.{ContextInitializer, Source}
import pl.touk.nussknacker.engine.api.test.ScenarioTestData
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
import pl.touk.nussknacker.engine.compile.nodecompilation.EvaluableLazyParameterCreator
import pl.touk.nussknacker.engine.flink.api.process.{
  CustomizableContextInitializerSource,
  FlinkSource,
  FlinkSourceTestSupport
}
import pl.touk.nussknacker.engine.flink.util.source.CollectionSource
import pl.touk.nussknacker.engine.testmode.TestDataPreparer

class SourceSpecificFormatStubbedSourcePreparer(
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
    val samples: List[Object]       = collectSamples(originalSource, nodeId)
    val watermarkStrategyForTestOpt = originalSource.watermarkStrategyForTest
    // setting timestamp as currentTimeMillis is good default
    // without this default we would run into issues with timestamp being Long.MIN_VALUE and
    // crashing time windows
    val improvedWatermarkStrategyForTest = watermarkStrategyForTestOpt.orElse(
      Some(
        WatermarkStrategy
          .forMonotonousTimestamps[Object]()
          .withTimestampAssigner(
            new SerializableTimestampAssigner[Object] {
              override def extractTimestamp(element: Object, recordTimestamp: Long): Long = System.currentTimeMillis()
            }
          )
      )
    )
    originalSource match {
      case sourceWithContextInitializer: CustomizableContextInitializerSource[Object @unchecked] =>
        new CollectionSource[Object](
          list = samples,
          watermarkStrategy = improvedWatermarkStrategyForTest,
          returnType = typingResult,
          boundedness = Boundedness.BOUNDED
        ) {
          override val contextInitializer: ContextInitializer[Object] = sourceWithContextInitializer.contextInitializer
        }
      case _ =>
        new CollectionSource[Object](
          list = samples,
          watermarkStrategy = improvedWatermarkStrategyForTest,
          returnType = typingResult,
          boundedness = Boundedness.BOUNDED
        )
    }
  }

  private def collectSamples(originalSource: Source, nodeId: NodeId): List[Object] = {
    val testRecordsForSource = scenarioTestData.inputRecords.filter(_.sourceId == nodeId)
    testDataPreparer.prepareRecordsForTest(originalSource, testRecordsForSource)
  }

}
