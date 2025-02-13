package pl.touk.nussknacker.engine.flink.util

import cats.data.ValidatedNel
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.flink.api.process.{
  FlinkCustomNodeContext,
  FlinkLazyParameterFunctionHelper,
  LazyParameterInterpreterFunction
}
import pl.touk.nussknacker.engine.flink.api.typeinformation.TypeInformationDetection
import pl.touk.nussknacker.engine.flink.typeinformation.KeyedValueType
import pl.touk.nussknacker.engine.util.KeyedValue

import scala.reflect.runtime.universe.TypeTag

// Must be in object because of Java interop (problems with package object) and abstract type StringKeyedValue[V]
object keyed {
  type StringKeyedValue[V] = KeyedValue[String, V]

  object StringKeyedValue {

    def apply[V](key: String, value: V): StringKeyedValue[V] = KeyedValue(key, value)

    def unapply[V](keyedValue: StringKeyedValue[V]): Option[(String, V)] = KeyedValue.unapply(keyedValue)

  }

  def typeInfo[K <: AnyRef, V <: AnyRef](
      flinkNodeContext: FlinkCustomNodeContext,
      key: LazyParameter[K],
      value: LazyParameter[V]
  ): TypeInformation[ValueWithContext[KeyedValue[K, V]]] = {
    flinkNodeContext.valueWithContextInfo.forType(
      KeyedValueType.info(
        TypeInformationDetection.instance.forType[K](key.returnType),
        TypeInformationDetection.instance.forType[V](value.returnType)
      )
    )
  }

  type GenericKeyedValue[K, V] = KeyedValue[K, V]

  object GenericKeyedValue {

    def apply[K, V](key: K, value: V): GenericKeyedValue[K, V] = GenericKeyedValue(key, value)

    def unapply[K, V](keyedValue: GenericKeyedValue[K, V]): Option[(K, V)] = KeyedValue.unapply(keyedValue)
  }

  abstract class BaseKeyedValueMapper[OutputKey <: AnyRef: TypeTag, OutputValue <: AnyRef: TypeTag]
      extends RichFlatMapFunction[Context, ValueWithContext[KeyedValue[OutputKey, OutputValue]]]
      with LazyParameterInterpreterFunction {

    protected implicit def toEvaluateFunctionConverterImpl: ToEvaluateFunctionConverter = toEvaluateFunctionConverter

    protected def prepareInterpreter(
        key: LazyParameter[OutputKey],
        value: LazyParameter[OutputValue]
    ): Context => KeyedValue[OutputKey, OutputValue] = {
      toEvaluateFunctionConverter.toEvaluateFunction(
        key.product(value).map(tuple => KeyedValue(tuple._1, tuple._2))
      )
    }

    protected def interpret(ctx: Context): KeyedValue[OutputKey, OutputValue]

    override def flatMap(
        ctx: Context,
        out: Collector[ValueWithContext[KeyedValue[OutputKey, OutputValue]]]
    ): Unit = {
      collectHandlingErrors(ctx, out) {
        ValueWithContext(interpret(ctx), ctx)
      }
    }

  }

  class KeyedValueMapper(
      protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
      key: LazyParameter[AnyRef],
      value: LazyParameter[AnyRef]
  ) extends BaseKeyedValueMapper[AnyRef, AnyRef] {

    private lazy val interpreter = prepareInterpreter(key, value)

    override protected def interpret(ctx: Context): KeyedValue[AnyRef, AnyRef] = interpreter(ctx)

  }

  class GenericKeyedValueMapper[Value <: AnyRef: TypeTag, Key <: AnyRef: TypeTag](
      protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
      key: LazyParameter[Key],
      value: LazyParameter[Value]
  ) extends BaseKeyedValueMapper[Key, Value] {

    def this(customNodeContext: FlinkCustomNodeContext, key: LazyParameter[Key], value: LazyParameter[Value]) =
      this(customNodeContext.lazyParameterHelper, key, value)

    private lazy val interpreter = prepareInterpreter(key, value)

    override protected def interpret(ctx: Context): KeyedValue[Key, Value] = interpreter(ctx)

  }

  class GenericKeyOnlyMapper[K <: AnyRef: TypeTag](
      protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
      key: LazyParameter[K]
  ) extends RichFlatMapFunction[Context, ValueWithContext[K]]
      with LazyParameterInterpreterFunction {

    protected implicit def toEvaluateFunctionConverterImpl: ToEvaluateFunctionConverter = toEvaluateFunctionConverter

    private lazy val interpreter = toEvaluateFunctionConverter.toEvaluateFunction(key)

    protected def interpret(ctx: Context): K = interpreter(ctx)

    override def flatMap(ctx: Context, out: Collector[ValueWithContext[K]]): Unit = {
      collectHandlingErrors(ctx, out) {
        ValueWithContext(interpret(ctx), ctx)
      }
    }

  }

  class StringKeyedValueMapper[T <: AnyRef: TypeTag](
      protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
      key: LazyParameter[CharSequence],
      value: LazyParameter[T]
  ) extends BaseKeyedValueMapper[String, T] {

    def this(customNodeContext: FlinkCustomNodeContext, key: LazyParameter[CharSequence], value: LazyParameter[T]) =
      this(customNodeContext.lazyParameterHelper, key, value)

    private lazy val interpreter = prepareInterpreter(key.map(transformKey), value)

    protected def transformKey(keyValue: CharSequence): String = {
      Option(keyValue).map(_.toString).getOrElse("")
    }

    override protected def interpret(ctx: Context): KeyedValue[String, T] = interpreter(ctx)

  }

  class StringKeyOnlyMapper(
      protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
      key: LazyParameter[CharSequence]
  ) extends RichFlatMapFunction[Context, ValueWithContext[String]]
      with LazyParameterInterpreterFunction {

    protected implicit def toEvaluateFunctionConverterImpl: ToEvaluateFunctionConverter = toEvaluateFunctionConverter

    private lazy val interpreter = toEvaluateFunctionConverter.toEvaluateFunction(key.map(transformKey))

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

    def enrichWithKey[K, V](ctx: Context, keyedValue: KeyedValue[K, V]): Context =
      enrichWithKey(ctx, keyedValue.key)

    def enrichWithKey[K, V](ctx: Context, key: K): Context =
      ctx.withVariable(VariableConstants.KeyVariableName, key)

    def contextTransformation(ctx: ValidationContext)(
        implicit nodeId: NodeId
    ): ValidatedNel[ProcessCompilationError, ValidationContext] =
      ctx.withVariableOverriden(VariableConstants.KeyVariableName, Typed[String], None)

  }

}
