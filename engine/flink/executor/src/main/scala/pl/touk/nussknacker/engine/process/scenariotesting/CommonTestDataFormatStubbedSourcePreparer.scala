package pl.touk.nussknacker.engine.process.scenariotesting

import pl.touk.nussknacker.engine.api.NodeId
import pl.touk.nussknacker.engine.api.context.ValidationContext
import pl.touk.nussknacker.engine.api.livedata.DataRecord
import pl.touk.nussknacker.engine.api.test.ScenarioTestCommonFormatJsonRecord
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.testmode.CommonTestDataFormatVariablesDecoder
import pl.touk.nussknacker.engine.util.watermarkstrategy.WatermarkStrategyOptions

object CommonTestDataFormatStubbedSourcePreparer {

  import pl.touk.nussknacker.engine.testmode.NonMapBasedRecordTypesOps._

  def prepareSubbedSource(
      testRecords: List[(ScenarioTestCommonFormatJsonRecord, Int)],
      sourceOutputValidationContext: ValidationContext,
      sourceId: NodeId,
      watermarkStrategyOptions: Option[WatermarkStrategyOptions]
  ): FlinkSource = {
    // We change record type because for now, we can't decode non-Map records from json - see FromJsonTypingResultBasedDecoder for details
    val outputValidationContextWithRecordsAsMaps =
      sourceOutputValidationContext.mapTypes(_.withoutValue.toMapBasedRecordTypes)
    val decoder = new CommonTestDataFormatVariablesDecoder(outputValidationContextWithRecordsAsMaps, sourceId)

    val decodedRecords = testRecords
      .map { case (record, testRecordIndex) =>
        val decodedVariables = decoder.decode(record.variables, testRecordIndex)
        DataRecord(decodedVariables, record.upstreamTimestamp)
      }
      .sortBy(_.upstreamTimestamp)

    new DataRecordsSource(decodedRecords, outputValidationContextWithRecordsAsMaps, watermarkStrategyOptions)
  }

}
