package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.streaming.api.datastream.DataStream
import org.apache.flink.streaming.api.scala.createTypeInformation
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.typed.{ReturningType, typing}
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, Unknown}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}
import pl.touk.nussknacker.engine.flink.typeinformation.ValueWithContextType

import java.util
import scala.collection.JavaConverters._

/**
  * Transforms the stream in a way that succeeding nodes are executed multiple times - once for every value
  * in the 'elements' list.
  */
object ForEachTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(@ParamName("Elements") elements: LazyParameter[java.util.Collection[AnyRef]],
             @OutputVariableName outputVariable: String): FlinkCustomStreamTransformation with ReturningType = {
    FlinkCustomStreamTransformation({ (stream: DataStream[Context], ctx: FlinkCustomNodeContext) =>
      stream
        .flatMap(ctx.lazyParameterHelper.lazyMapFunction(elements))
        .flatMap((valueWithContext: ValueWithContext[util.Collection[AnyRef]], c: Collector[ValueWithContext[AnyRef]]) => {
          valueWithContext.value.asScala
            .map(new ValueWithContext[AnyRef](_, valueWithContext.context))
            .foreach(c.collect)
        }, ValueWithContextType.info[AnyRef](ctx))
    }, returnType(elements))
  }

  private def returnType(elements: LazyParameter[util.Collection[AnyRef]]): typing.TypingResult = elements.returnType match {
    case tc: SingleTypingResult if tc.objType.canBeSubclassOf(Typed[util.Collection[_]]) && tc.objType.params.nonEmpty =>
      tc.objType.params.head
    case _ => Unknown
  }
}
