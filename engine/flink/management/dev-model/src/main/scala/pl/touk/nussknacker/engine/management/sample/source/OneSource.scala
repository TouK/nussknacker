package pl.touk.nussknacker.engine.management.sample.source

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSource
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator

class OneSource extends BasicFlinkSource[String] {

  override def timestampAssigner: Option[Nothing] = None

  @silent("deprecated")
  override def flinkSourceFunction: SourceFunction[String] = new SourceFunction[String] {

    var run = true

    var emited = false

    override def cancel(): Unit = {
      run = false
    }

    override def run(ctx: SourceContext[String]): Unit = {
      while (run) {
        if (!emited) ctx.collect(DevProcessConfigCreator.oneElementValue)
        emited = true
        Thread.sleep(1000)
      }
    }

  }

  override val typeInformation: TypeInformation[String] = TypeInformation.of(classOf[String])
}
