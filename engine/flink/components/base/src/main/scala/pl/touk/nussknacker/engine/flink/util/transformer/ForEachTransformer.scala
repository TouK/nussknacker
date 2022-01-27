package pl.touk.nussknacker.engine.flink.util.transformer

import org.apache.flink.api.scala.createTypeInformation
import org.apache.flink.streaming.api.scala.DataStream
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkCustomStreamTransformation}

import scala.collection.JavaConverters._

/**
  * Transforms the stream in a way that succeeding nodes are executed multiple times - once for every value
  * in the 'elements' list.
  */
object ForEachTransformer extends CustomStreamTransformer {

  @MethodToInvoke(returnType = classOf[Object])
  def invoke(@ParamName("Elements") elements: LazyParameter[java.util.Collection[AnyRef]],
             @OutputVariableName outputVariable: String): FlinkCustomStreamTransformation = {
    FlinkCustomStreamTransformation { (stream: DataStream[Context], ctx: FlinkCustomNodeContext) =>
      stream
        .flatMap(ctx.lazyParameterHelper.lazyMapFunction(elements))
        .flatMap(valueWithContext => valueWithContext.value.asScala.map(
          new ValueWithContext[AnyRef](_, valueWithContext.context)
        ))
    }
  }
}


