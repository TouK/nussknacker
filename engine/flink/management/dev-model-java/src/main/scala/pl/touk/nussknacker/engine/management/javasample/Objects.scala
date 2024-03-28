package pl.touk.nussknacker.engine.management.javasample

import com.github.ghik.silencer.silent
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.functions.source.SourceFunction
import pl.touk.nussknacker.engine.api.process.{SinkFactory, SourceFactory, WithCategories}
import pl.touk.nussknacker.engine.flink.api.process.BasicFlinkSource
import pl.touk.nussknacker.engine.flink.api.timestampwatermark.TimestampWatermarkHandler
import pl.touk.nussknacker.engine.flink.util.sink.EmptySink

class Objects extends Serializable {

  def source: WithCategories[SourceFactory] =
    WithCategories.anyCategory(SourceFactory.noParamUnboundedStreamFactory[Model](new BasicFlinkSource[Model] {

      @silent("deprecated")
      override def flinkSourceFunction: SourceFunction[Model] = new SourceFunction[Model] {

        override def cancel(): Unit = {}

        override def run(ctx: SourceFunction.SourceContext[Model]): Unit = {
          while (true) {
            Thread.sleep(10000)
          }
        }

      }

      override val typeInformation: TypeInformation[Model] = TypeInformation.of(classOf[Model])

      override def timestampAssigner: Option[TimestampWatermarkHandler[Model]] = None
    }))

  def sink = WithCategories.anyCategory(SinkFactory.noParam(EmptySink))

}
