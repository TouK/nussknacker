package pl.touk.nussknacker.engine.management.sample.source

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStreamSource
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import pl.touk.nussknacker.engine.api.CirceUtil
import pl.touk.nussknacker.engine.api.process.{BasicContextInitializer, ContextInitializer}
import pl.touk.nussknacker.engine.api.test.{TestRecord, TestRecordParser}
import pl.touk.nussknacker.engine.api.typed.typing.Unknown
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkSourceTestSupport,
  FlinkStandardSourceUtils,
  StandardFlinkSource
}
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.{
  StandardTimestampWatermarkHandler,
  TimestampWatermarkHandler
}

import java.time.Duration
import java.util.concurrent.atomic.{AtomicBoolean, AtomicLong}

//this not ending source is more reliable in tests than CollectionSource, which terminates quickly
class NoEndingSource extends StandardFlinkSource[String] with FlinkSourceTestSupport[String] {

  @silent("deprecated")
  override def initialSourceStream(
      env: StreamExecutionEnvironment,
      flinkNodeContext: FlinkCustomNodeContext
  ): DataStreamSource[String] = {
    val flinkSourceFunction: SourceFunction[String] = new SourceFunction[String] {
      var running       = true
      var counter       = new AtomicLong()
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
    FlinkStandardSourceUtils.createSourceStream(
      env = env,
      sourceFunction = flinkSourceFunction,
      typeInformation = TypeInformation.of(classOf[String])
    )
  }

  override def contextInitializer: ContextInitializer[String] = new BasicContextInitializer[String](Unknown)

  override val typeInformation: TypeInformation[String] = TypeInformation.of(classOf[String])

  override def timestampAssigner: Option[TimestampWatermarkHandler[String]] = Option(
    StandardTimestampWatermarkHandler
      .boundedOutOfOrderness[String]((_: String) => System.currentTimeMillis(), Duration.ofMinutes(10))
  )

  override def timestampAssignerForTest: Option[TimestampWatermarkHandler[String]] = timestampAssigner

  override def testRecordParser: TestRecordParser[String] = (testRecord: TestRecord) =>
    CirceUtil.decodeJsonUnsafe[String](testRecord.json)
}
