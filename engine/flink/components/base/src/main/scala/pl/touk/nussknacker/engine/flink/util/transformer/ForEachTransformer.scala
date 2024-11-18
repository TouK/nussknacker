package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, Unknown}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}

import java.util
import scala.jdk.CollectionConverters._

/**
  * Transforms the stream in a way that succeeding nodes are executed multiple times - once for every value
  * in the 'elements' list.
  */
object ForEachTransformer extends CustomStreamTransformer with Serializable {

  import pl.touk.nussknacker.engine.flink.api.datastream.DataStreamImplicits._

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(
      @ParamName("Elements") elements: LazyParameter[util.Collection[AnyRef]],
      @OutputVariableName outputVariable: String
  ): FlinkCustomStreamTransformation with ReturningType = {
    FlinkCustomStreamTransformation(
      { (stream: DataStream[Context], ctx: FlinkCustomNodeContext) =>
        stream
          .flatMap(elements)(ctx)
          .flatMap(
            (valueWithContext: ValueWithContext[util.Collection[AnyRef]], c: Collector[ValueWithContext[AnyRef]]) => {
              valueWithContext.value.asScala.zipWithIndex
                .map { case (partToRun, index) =>
                  new ValueWithContext[AnyRef](partToRun, valueWithContext.context.appendIdSuffix(index.toString))
                }
                .foreach(c.collect)
            },
            ctx.valueWithContextInfo.forType[AnyRef](returnType(elements))
          )
      },
      returnType(elements)
    )
  }

  private def returnType(elements: LazyParameter[util.Collection[AnyRef]]): typing.TypingResult =
    elements.returnType match {
      case tc: SingleTypingResult
          if tc.runtimeObjType.canBeConvertedTo(
            Typed[util.Collection[_]]
          ) && tc.runtimeObjType.params.nonEmpty =>
        tc.runtimeObjType.params.head
      case _ => Unknown
    }

}
