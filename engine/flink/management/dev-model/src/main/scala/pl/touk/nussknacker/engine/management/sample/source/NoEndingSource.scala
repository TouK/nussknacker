package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.api.common.eventtime.WatermarkStrategy
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.{DataStream, DataStreamSource}
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import pl.touk.nussknacker.engine.api.{CirceUtil, Context}
import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits.DataStreamExtension
import pl.touk.nussknacker.engine.flink.api.process._
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.WatermarkStrategyUtils

import java.time.Duration
import java.util.concurrent.atomic.AtomicBoolean
import scala.annotation.nowarn

//this not ending source is more reliable in tests than CollectionSource, which terminates quickly
class NoEndingSource(val implementWatermarkStrategyForTest: Boolean)
    extends FlinkSource
    with CustomizableContextInitializerSource[String]
    with FlinkSourceTestSupport[String] {

  override final def contextStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStream[Context] = {
    // 1. set UID and override source name
    val rawSourceWithUid = sourceStream(env).setUidAndNameToNodeId(flinkNodeContext.nodeId)

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

  @nowarn("cat=deprecation")
  private def sourceStream(
      env: StreamExecutionEnvironment,
  ): DataStreamSource[String] = {
    val flinkSourceFunction: SourceFunction[String] = new SourceFunction[String] {
      var running       = true
      val afterFirstRun = new AtomicBoolean(false)

      override def cancel(): Unit = {
        running = false
      }

      override def run(ctx: SourceContext[String]): Unit = {
        val r = new scala.util.Random
        while (running) {
          if (afterFirstRun.getAndSet(true)) {
            ctx.collect("TestInput" + r.nextInt(10))
          } else {
            ctx.collect("TestInput1")
          }
          Thread.sleep(2000)
        }
      }

    }
    env.addSource(flinkSourceFunction, TypeInformation.of(classOf[String]))
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
