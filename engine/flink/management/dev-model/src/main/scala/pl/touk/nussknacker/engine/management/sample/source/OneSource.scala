package pl.touk.nussknacker.engine.management.sample.source

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import org.apache.flink.streaming.api.functions.source.SourceFunction.SourceContext
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSource
import pl.touk.nussknacker.engine.management.sample.DevProcessConfigCreator
import org.apache.flink.streaming.api.scala._

class OneSource extends BasicFlinkSource[String] {

  override def timestampAssigner: Option[Nothing] = None

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

  override val typeInformation: TypeInformation[String] = implicitly[TypeInformation[String]]
}
