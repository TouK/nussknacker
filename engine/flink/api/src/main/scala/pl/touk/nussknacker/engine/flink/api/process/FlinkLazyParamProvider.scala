package pl.touk.nussknacker.engine.flink.api.process


import org.apache.flink.api.common.functions._
import org.apache.flink.configuration.Configuration
import pl.touk.nussknacker.engine.api._


/*
  This is main class that allows to use LazyParameter[T] for flink processes.
  Helper traits are provided
 */
case class FlinkLazyParamProvider(creator: RuntimeContext => LazyParameterInterpreter) {

  /*
     Helper that allows for easy mapping:
        stream.map(context.nodeServices.lazyMapFunction(keyBy))
        .keyBy(_.value)
        @see AggregateTransformer
   */
  def lazyMapFunction[T](intepreter: LazyParameter[T]): MapFunction[Context, ValueWithContext[T]]
    = new LazyParameterMapFunction[T](intepreter, this)

  /*
     Helper that allows for easy filtering:
     stream.filter(ctx.nodeServices.lazyFilterFunction(expression))
     @see CustomFilter class
   */
  def lazyFilterFunction(intepreter: LazyParameter[Boolean]): FilterFunction[Context]
    = new LazyParameterFilterFunction(intepreter, this)


}


class LazyParameterFilterFunction(interpreter: LazyParameter[Boolean], customNodeFunctions: FlinkLazyParamProvider)
  extends AbstractOneParamLazyParameterFunction(interpreter, customNodeFunctions) with FilterFunction[Context] {

  override def filter(value: Context): Boolean = {
    evaluateParameter(value)
  }

}

class LazyParameterMapFunction[T](interpreter: LazyParameter[T], customNodeFunctions: FlinkLazyParamProvider)
  extends AbstractOneParamLazyParameterFunction(interpreter, customNodeFunctions) with MapFunction[Context, ValueWithContext[T]] {

  override def map(value: Context): ValueWithContext[T] = {
    ValueWithContext(evaluateParameter(value), value)
  }

}

abstract class AbstractLazyParameterInterpreterFunction(val customNodeFunctions: FlinkLazyParamProvider)
  extends AbstractRichFunction with LazyParameterInterpreterFunction

abstract class AbstractOneParamLazyParameterFunction[T](val parameter: LazyParameter[T],
                                                        customNodeFunctions: FlinkLazyParamProvider)
  extends AbstractLazyParameterInterpreterFunction(customNodeFunctions) with OneParamLazyParameterFunction[T]


trait LazyParameterInterpreterFunction extends RichFunction with Serializable {

  protected def customNodeFunctions: FlinkLazyParamProvider

  protected var lazyParameterInterpreter : LazyParameterInterpreter = _

  override def close(): Unit = {
    lazyParameterInterpreter.close()
  }

  //TODO: how can we make sure this will invoke super.open(...) (can't do it directly...)
  override def open(parameters: Configuration): Unit = {
    lazyParameterInterpreter = customNodeFunctions.creator(getRuntimeContext)
  }

}

trait OneParamLazyParameterFunction[T] extends LazyParameterInterpreterFunction {

  protected def parameter: LazyParameter[T]

  protected var evaluateParameter: Context => T = _

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    evaluateParameter = lazyParameterInterpreter.syncInterpretationFunction(parameter)
  }

}
