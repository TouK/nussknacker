package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.connector.source._
import org.apache.flink.api.connector.source.util.ratelimit.RateLimiterStrategy
import org.apache.flink.connector.datagen.source.DataGeneratorSource
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import pl.touk.nussknacker.engine.api.{CirceUtil, Context}
import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.WatermarkStrategyUtils

import java.time.Duration

//this not ending source is more reliable in tests than CollectionSource, which terminates quickly
class NoEndingSource(val implementWatermarkStrategyForTest: Boolean)
    extends FlinkSource
    with CustomizableContextInitializerSource[String]
    with FlinkSourceTestSupport[String] {

  override final def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    // 1. set UID and operator name
    val rawSourceWithUid = env
      .fromSource(
        flinkSource,
        watermarkStrategy.getOrElse(WatermarkStrategy.noWatermarks()),
        flinkNodeContext.nodeId.value,
        TypeInformation.of(classOf[String])
      )
      .uid(flinkNodeContext.nodeId.value)
      .name(flinkNodeContext.nodeName.value)

    // 2. assign timestamp and watermark policy
    val rawSourceWithUidAndTimestamp = watermarkStrategy
      .map(rawSourceWithUid.assignTimestampsAndWatermarks)
      .getOrElse(rawSourceWithUid)

    // 3. initialize Context and spool Context to the stream
    rawSourceWithUidAndTimestamp
      .map(
        new FlinkContextInitializingFunction(
          contextInitializer,
          flinkNodeContext.nodeId,
          flinkNodeContext.convertToEngineRuntimeContext
        ),
        flinkNodeContext.contextTypeInfo
      )
  }

  private def flinkSource: Source[String, _, _] = {
    val r = new scala.util.Random
    new DataGeneratorSource[String](
      (index: java.lang.Long) => if (index == 0) "TestInput1" else "TestInput" + r.nextInt(10),
      Long.MaxValue,
      RateLimiterStrategy.perSecond(0.5),
      Types.STRING
    )
  }

  private val watermarkStrategy: Option[WatermarkStrategy[String]] =
    if (!implementWatermarkStrategyForTest)
      None
    else
      Option(
        WatermarkStrategyUtils
          .boundedOutOfOrderness[String](
            assigner = (_: String, _: Long) => System.currentTimeMillis(),
            maxOutOfOrderness = Duration.ofMinutes(10),
          )
      )

  override def watermarkStrategyForTest: Option[WatermarkStrategy[String]] = watermarkStrategy

  override def testRecordParser: TestRecordParser[String] = (testRecords: List[TestRecord]) =>
    testRecords.map { testRecord =>
      CirceUtil.decodeJsonUnsafe[String](testRecord.json)
    }

}
