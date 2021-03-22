package pl.touk.nussknacker.engine.flink.util.timestamp

import com.github.ghik.silencer.silent
import org.apache.flink.streaming.api.functions.AssignerWithPunctuatedWatermarks
import org.apache.flink.streaming.api.watermark.Watermark

import scala.annotation.nowarn

@silent("deprecated")
@nowarn("cat=deprecation")
abstract class BoundedOutOfOrdernessPunctuatedExtractor[T](maxOutOfOrdernessMillis: Long)
  extends AssignerWithPunctuatedWatermarks[T] {

  override def checkAndGetNextWatermark(lastElement: T, extractedTimestamp: Long): Watermark = {
    new Watermark(extractedTimestamp - maxOutOfOrdernessMillis)
  }

}




