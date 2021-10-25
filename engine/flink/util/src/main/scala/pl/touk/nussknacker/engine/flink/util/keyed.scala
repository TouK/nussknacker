package pl.touk.nussknacker.engine.flink.util

import cats.data.ValidatedNel
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.context.ProcessCompilationError.NodeId
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, ValueWithContext, VariableConstants}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkCustomNodeContext, FlinkLazyParameterFunctionHelper, LazyParameterInterpreterFunction}
import pl.touk.nussknacker.engine.util.KeyedValue

import scala.reflect.runtime.universe.TypeTag

// Must be in object because of Java interop (problems with package object) and abstract type StringKeyedValue[V]
object keyed {
  type StringKeyedValue[V] = KeyedValue[String, V]

  object StringKeyedValue {

    def apply[V](key: String, value: V): StringKeyedValue[V] = KeyedValue(key, value)

    def unapply[V](keyedValue: StringKeyedValue[V]): Option[(String, V)] = KeyedValue.unapply(keyedValue)

    // It is helper function for interop with java - e.g. in case when you want to have StringKeyedEvent[POJO]
    def typeInformation[V](valueTypeInformation: TypeInformation[V]): TypeInformation[KeyedValue[String, V]] = {
      KeyValueHelperTypeInformation.typeInformation(implicitly[TypeInformation[String]], valueTypeInformation)
    }

  }

  abstract class BaseKeyedValueMapper[OutputKey <: AnyRef : TypeTag, OutputValue <: AnyRef : TypeTag] extends RichFlatMapFunction[Context, ValueWithContext[KeyedValue[OutputKey, OutputValue]]] with LazyParameterInterpreterFunction {

    protected implicit def lazyParameterInterpreterImpl: LazyParameterInterpreter = lazyParameterInterpreter

    protected def prepareInterpreter(key: LazyParameter[OutputKey], value: LazyParameter[OutputValue]): Context => KeyedValue[OutputKey, OutputValue] = {
      lazyParameterInterpreter.syncInterpretationFunction(
        key.product(value).map(tuple => KeyedValue(tuple._1, tuple._2)))
    }

    protected def interpret(ctx: Context): KeyedValue[OutputKey, OutputValue]


    override def flatMap(ctx: Context, out: Collector[ValueWithContext[KeyedValue[OutputKey, OutputValue]]]): Unit = {
      collectHandlingErrors(ctx, out) {
        ValueWithContext(interpret(ctx), ctx)
      }
    }

  }

  class KeyedValueMapper(protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper, key: LazyParameter[AnyRef], value: LazyParameter[AnyRef])
    extends BaseKeyedValueMapper[AnyRef, AnyRef] {

    private lazy val interpreter = prepareInterpreter(key, value)

    override protected def interpret(ctx: Context): KeyedValue[AnyRef, AnyRef] = interpreter(ctx)

  }


  /*
     We pass LazyParameter => ... as value here, because in some places we want to
     perform further mapping/operations on LazyParameter from user, and LazyParameter.map
     requires LazyParameterInterpreter
   */
  class StringKeyedValueMapper[T <: AnyRef : TypeTag](protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                                                      key: LazyParameter[CharSequence],
                                                      value: LazyParameterInterpreter => LazyParameter[T])
    extends BaseKeyedValueMapper[String, T] {

    def this(customNodeContext: FlinkCustomNodeContext,
             key: LazyParameter[CharSequence],
             value: LazyParameter[T]) = this(customNodeContext.lazyParameterHelper, key, _ => value)

    private lazy val interpreter = prepareInterpreter(key.map(transformKey), value(lazyParameterInterpreter))

    protected def transformKey(keyValue: CharSequence): String = {
      Option(keyValue).map(_.toString).getOrElse("")
    }

    override protected def interpret(ctx: Context): KeyedValue[String, T] = interpreter(ctx)

  }

  class StringKeyOnlyMapper(protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper, key: LazyParameter[CharSequence])
    extends RichFlatMapFunction[Context, ValueWithContext[String]] with LazyParameterInterpreterFunction {

    protected implicit def lazyParameterInterpreterImpl: LazyParameterInterpreter = lazyParameterInterpreter

    private lazy val interpreter = lazyParameterInterpreter.syncInterpretationFunction(key.map(transformKey))

    protected def interpret(ctx: Context): String = interpreter(ctx)

    protected def transformKey(keyValue: CharSequence): String = {
      Option(keyValue).map(_.toString).getOrElse("")
    }


    override def flatMap(ctx: Context, out: Collector[ValueWithContext[String]]): Unit = {
      collectHandlingErrors(ctx, out) {
        ValueWithContext(interpret(ctx), ctx)
      }
    }

  }

  object KeyEnricher {

    def enrichWithKey[V](ctx: Context, keyedValue: StringKeyedValue[V]): Context =
      enrichWithKey(ctx, keyedValue.key)

    def enrichWithKey[V](ctx: Context, key: String): Context =
      ctx.withVariable(VariableConstants.KeyVariableName, key)

    def contextTransformation(ctx: ValidationContext)(implicit nodeId: NodeId): ValidatedNel[ProcessCompilationError, ValidationContext] =
      ctx.withVariableOverriden(VariableConstants.KeyVariableName, Typed[String], None)

  }

}
