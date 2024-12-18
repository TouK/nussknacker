package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.functions._
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.LazyParameter.Evaluate
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.component.NodeComponentInfo
import pl.touk.nussknacker.engine.flink.api.exception.ExceptionHandler

/*
  This is helper class that allows to evaluate LazyParameter[T] in Flink functions.
 */
class FlinkLazyParameterFunctionHelper(
    val exceptionComponentInfo: NodeComponentInfo,
    val exceptionHandler: RuntimeContext => ExceptionHandler,
    val createInterpreter: RuntimeContext => ToEvaluateFunctionConverter with AutoCloseable
) extends Serializable {

  /*
     Helper that allows for easy mapping:
        stream.flatMap(context.nodeServices.lazyMapFunction(keyBy))
        .keyBy(_.value)
        @see AggregateTransformer
   */
  def lazyMapFunction[T <: AnyRef](
      parameter: LazyParameter[T]
  ): FlatMapFunction[Context, ValueWithContext[T]] =
    new LazyParameterMapFunction[T](parameter, this)

  /*
     Helper that allows for easy filtering:
     stream.filter(ctx.nodeServices.lazyFilterFunction(expression))
     @see CustomFilter class
   */
  def lazyFilterFunction(parameter: LazyParameter[java.lang.Boolean]): FilterFunction[Context] =
    new LazyParameterFilterFunction(parameter, this)

}

class LazyParameterFilterFunction(
    parameter: LazyParameter[java.lang.Boolean],
    lazyParameterHelper: FlinkLazyParameterFunctionHelper
) extends AbstractOneParamLazyParameterFunction(parameter, lazyParameterHelper)
    with FilterFunction[Context] {

  override def filter(value: Context): Boolean = {
    val handled: Option[Boolean] = handlingErrors(value) {
      evaluateParameter(value)
    }
    handled.getOrElse(false)
  }

}

class LazyParameterMapFunction[T <: AnyRef](
    parameter: LazyParameter[T],
    lazyParameterHelper: FlinkLazyParameterFunctionHelper
) extends AbstractOneParamLazyParameterFunction(parameter, lazyParameterHelper)
    with FlatMapFunction[Context, ValueWithContext[T]] {

  override def flatMap(value: Context, out: Collector[ValueWithContext[T]]): Unit = {
    collectHandlingErrors(value, out) {
      ValueWithContext(evaluateParameter(value), value)
    }
  }

}

abstract class AbstractOneParamLazyParameterFunction[T <: AnyRef](
    val parameter: LazyParameter[T],
    val lazyParameterHelper: FlinkLazyParameterFunctionHelper
) extends AbstractRichFunction
    with OneParamLazyParameterFunction[T]

abstract class AbstractLazyParameterInterpreterFunction(val lazyParameterHelper: FlinkLazyParameterFunctionHelper)
    extends AbstractRichFunction
    with LazyParameterInterpreterFunction

/*
  Helper trait for situation when you are using one lazy parameter.
 */
trait OneParamLazyParameterFunction[T <: AnyRef] extends LazyParameterInterpreterFunction { self: RichFunction =>

  protected def parameter: LazyParameter[T]

  private var _evaluateParameter: Evaluate[T] = _

  protected def evaluateParameter(ctx: Context): T =
    _evaluateParameter(ctx)

  override def open(openContext: OpenContext): Unit = {
    super.open(openContext)
    _evaluateParameter = toEvaluateFunctionConverter.toEvaluateFunction(parameter)
  }

}

/**
  LazyParameterInterpreter is used to evaluate LazyParamater[T]. It has to be tied to operator's lifecycle to avoid
  leaking of resources. Because of this if you need to evaluate parameter, you always need to mixin this trait.
  Please note that exception thrown during LazyParameter evaluation should be handled - e.g. be wrapping in handling/collect methods
 */
trait LazyParameterInterpreterFunction { self: RichFunction =>

  protected def lazyParameterHelper: FlinkLazyParameterFunctionHelper

  protected var toEvaluateFunctionConverter: ToEvaluateFunctionConverter with AutoCloseable = _

  protected var exceptionHandler: ExceptionHandler = _

  override def close(): Unit = {
    if (toEvaluateFunctionConverter != null)
      toEvaluateFunctionConverter.close()
    if (exceptionHandler != null)
      exceptionHandler.close()
  }

  // TODO: how can we make sure this will invoke super.open(...) (can't do it directly...)
  override def open(openContext: OpenContext): Unit = {
    toEvaluateFunctionConverter = lazyParameterHelper.createInterpreter(getRuntimeContext)
    exceptionHandler = lazyParameterHelper.exceptionHandler(getRuntimeContext)
  }

  /**
    * This method should be use to handle exception that can occur during e.g. LazyParameter evaluation
    */
  def handlingErrors[T](context: Context)(action: => T): Option[T] =
    exceptionHandler.handling(Some(lazyParameterHelper.exceptionComponentInfo), context)(action)

  /**
    * This method should be use to handle exception that can occur during e.g. LazyParameter evaluation in
    * flatMap-like operators/functions
    */
  def collectIterableHandlingErrors[T](context: Context, collector: Collector[T])(
      action: => Iterable[T]
  ): Unit =
    handlingErrors(context)(action)
      .foreach(data => data.foreach(collector.collect))

  def collectHandlingErrors[T](context: Context, collector: Collector[T])(action: => T): Unit =
    collectIterableHandlingErrors(context, collector)(List(action))

}
