package pl.touk.nussknacker.engine.flink.api.process

import org.apache.flink.api.common.functions.{RuntimeContext, _}
import org.apache.flink.configuration.Configuration
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.exception.FlinkEspExceptionHandler

/*
  This is helper class that allows to evaluate LazyParameter[T] in Flink functions.
 */
class FlinkLazyParameterFunctionHelper(val nodeId: String,
                                       val exceptionHandler: RuntimeContext => FlinkEspExceptionHandler,
                                       val createInterpreter: RuntimeContext => LazyParameterInterpreter) extends Serializable {

  /*
     Helper that allows for easy mapping:
        stream.map(context.nodeServices.lazyMapFunction(keyBy))
        .keyBy(_.value)
        @see AggregateTransformer
   */
  def lazyMapFunction[T <: AnyRef](parameter: LazyParameter[T]): FlatMapFunction[Context, ValueWithContext[T]]
  = new LazyParameterMapFunction[T](parameter, this)

  /*
     Helper that allows for easy filtering:
     stream.filter(ctx.nodeServices.lazyFilterFunction(expression))
     @see CustomFilter class
   */
  def lazyFilterFunction(parameter: LazyParameter[java.lang.Boolean]): FilterFunction[Context]
  = new LazyParameterFilterFunction(parameter, this)

}

class LazyParameterFilterFunction(parameter: LazyParameter[java.lang.Boolean], lazyParameterHelper: FlinkLazyParameterFunctionHelper)
  extends AbstractOneParamLazyParameterFunction(parameter, lazyParameterHelper) with FilterFunction[Context] {

  override def filter(value: Context): Boolean = {
    val handled: Option[Boolean] = handling(value) {
      evaluateParameter(value)
    }
    handled.getOrElse(false)
  }

}

class LazyParameterMapFunction[T <: AnyRef](parameter: LazyParameter[T], lazyParameterHelper: FlinkLazyParameterFunctionHelper)
  extends AbstractOneParamLazyParameterFunction(parameter, lazyParameterHelper) with FlatMapFunction[Context, ValueWithContext[T]] {


  override def flatMap(value: Context, out: Collector[ValueWithContext[T]]): Unit = {
    collect(value, out) {
      ValueWithContext(evaluateParameter(value), value)
    }
  }

}

abstract class AbstractOneParamLazyParameterFunction[T <: AnyRef](val parameter: LazyParameter[T],
                                                                  val lazyParameterHelper: FlinkLazyParameterFunctionHelper)
  extends AbstractRichFunction with OneParamLazyParameterFunction[T]

abstract class AbstractLazyParameterInterpreterFunction(val lazyParameterHelper: FlinkLazyParameterFunctionHelper)
  extends AbstractRichFunction with LazyParameterInterpreterFunction

/*
  Helper trait for situation when you are using one lazy parameter.
 */
trait OneParamLazyParameterFunction[T <: AnyRef] extends LazyParameterInterpreterFunction { self: RichFunction =>

  protected def parameter: LazyParameter[T]

  private var _evaluateParameter: Context => T = _

  protected def evaluateParameter(ctx: Context): T =
    _evaluateParameter(ctx)

  override def open(parameters: Configuration): Unit = {
    super.open(parameters)
    _evaluateParameter = lazyParameterInterpreter.syncInterpretationFunction(parameter)
  }

}

/*
  For lazy parameter evaluations is used LazyParameterInterpreter. It need to be used in operators lifecycle to avoid
  leaking of resources. Because of this if you need to evaluate parameter, you always need to mixin this trait.
 */
trait LazyParameterInterpreterFunction { self: RichFunction =>

  protected def lazyParameterHelper: FlinkLazyParameterFunctionHelper

  protected var lazyParameterInterpreter : LazyParameterInterpreter = _

  protected var exceptionHandler: FlinkEspExceptionHandler = _

  private val nodeId = Some(lazyParameterHelper.nodeId)

  override def close(): Unit = {
    if (lazyParameterInterpreter != null)
      lazyParameterInterpreter.close()
    if (exceptionHandler != null)
      exceptionHandler.close()
  }

  //TODO: how can we make sure this will invoke super.open(...) (can't do it directly...)
  override def open(parameters: Configuration): Unit = {
    lazyParameterInterpreter = lazyParameterHelper.createInterpreter(getRuntimeContext)
    exceptionHandler = lazyParameterHelper.exceptionHandler(getRuntimeContext)
  }

  def handling[T](context: Context)(action: => T): Option[T] = exceptionHandler.handling(nodeId, context)(action)

  def collect[T](context: Context, collector: Collector[T])(action: => T): Unit = handling(context)(action).foreach(collector.collect)

}