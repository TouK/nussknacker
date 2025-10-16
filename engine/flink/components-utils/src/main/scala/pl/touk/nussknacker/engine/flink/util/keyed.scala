package pl.touk.nussknacker.engine.flink.util

import cats.data.ValidatedNel
import org.apache.flink.api.common.functions.RichFlatMapFunction
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.util.Collector
import pl.touk.nussknacker.engine.api._
import pl.touk.nussknacker.engine.api.context.{ProcessCompilationError, ValidationContext}
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.parameter.ParameterName
import pl.touk.nussknacker.engine.api.typed.typing.TypingResult
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

  import BaseKeyedValueMapper._

  abstract class BaseKeyedValueMapper[OutputKey <: AnyRef: TypeTag, OutputValue <: AnyRef: TypeTag]
      extends RichFlatMapFunction[Context, ValueWithContext[KeyedValue[OutputKey, OutputValue]]]
      with LazyParameterInterpreterFunction {

    protected implicit def toEvaluateFunctionConverterImpl: ToEvaluateFunctionConverter = toEvaluateFunctionConverter

    protected def prepareInterpreter(
        key: LazyParameter[Either[InterpretationError, OutputKey]],
        value: LazyParameter[OutputValue]
    ): Context => Either[InterpretationError, KeyedValue[OutputKey, OutputValue]] = {
      toEvaluateFunctionConverter.toEvaluateFunction(
        key.product(value).map(tuple => tuple._1.map(KeyedValue(_, tuple._2)))
      )
    }

    protected def interpret(ctx: Context): Either[InterpretationError, KeyedValue[OutputKey, OutputValue]]

    override def flatMap(
        ctx: Context,
        out: Collector[ValueWithContext[KeyedValue[OutputKey, OutputValue]]]
    ): Unit = {
      collectHandlingErrors(ctx, out) {
        interpret(ctx) match {
          case Right(value) => ValueWithContext(value, ctx)
          case Left(InterpretationError.NullKeyError(parameterName)) =>
            throw new NullKeyException(parameterName.value)
        }
      }
    }

  }

  final case class KeyOptions(allowNullableKeys: Boolean)

  private[keyed] object BaseKeyedValueMapper {
    sealed trait InterpretationError

    object InterpretationError {
      final case class NullKeyError(parameterName: ParameterName) extends InterpretationError
    }

    def checkKey[T](
        key: T,
        keyParameterName: ParameterName,
        options: KeyOptions
    ): Either[InterpretationError.NullKeyError, T] = {
      Either.cond(
        options.allowNullableKeys || key != null,
        key,
        InterpretationError.NullKeyError(keyParameterName)
      )
    }

  }

  class KeyedValueMapper(
      protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
      key: LazyParameter[AnyRef],
      keyParameterName: ParameterName,
      keyOptions: KeyOptions,
      value: LazyParameter[AnyRef]
  ) extends BaseKeyedValueMapper[AnyRef, AnyRef] {

    private lazy val interpreter = prepareInterpreter(key.map(checkKey(_, keyParameterName, keyOptions)), value)

    override protected def interpret(ctx: Context): Either[InterpretationError, KeyedValue[AnyRef, AnyRef]] =
      interpreter(ctx)

  }

  class GenericKeyedValueMapper[Value <: AnyRef: TypeTag, Key <: AnyRef: TypeTag](
      protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
      key: LazyParameter[Key],
      keyParameterName: ParameterName,
      keyOptions: KeyOptions,
      value: LazyParameter[Value]
  ) extends BaseKeyedValueMapper[Key, Value] {

    def this(
        customNodeContext: FlinkCustomNodeContext,
        key: LazyParameter[Key],
        keyParameterName: ParameterName,
        keyOptions: KeyOptions,
        value: LazyParameter[Value]
    ) =
      this(customNodeContext.lazyParameterHelper, key, keyParameterName, keyOptions, value)

    private lazy val interpreter = prepareInterpreter(key.map(transformKey), value)

    override protected def interpret(ctx: Context): Either[InterpretationError, KeyedValue[Key, Value]] = interpreter(
      ctx
    )

    private def transformKey(keyValue: Key): Either[InterpretationError, Key] = {
      checkKey(keyValue, keyParameterName, keyOptions)
    }

  }

  class StringKeyedValueMapper[T <: AnyRef: TypeTag](
      protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
      key: LazyParameter[CharSequence],
      keyParameterName: ParameterName,
      keyOptions: KeyOptions,
      value: LazyParameter[T]
  ) extends BaseKeyedValueMapper[String, T] {

    def this(
        customNodeContext: FlinkCustomNodeContext,
        key: LazyParameter[CharSequence],
        keyParameterName: ParameterName,
        keyOptions: KeyOptions,
        value: LazyParameter[T]
    ) =
      this(customNodeContext.lazyParameterHelper, key, keyParameterName, keyOptions, value)

    private lazy val interpreter = prepareInterpreter(key.map(transformKey), value)

    protected def transformKey(keyValue: CharSequence): Either[InterpretationError, String] = {
      checkKey(keyValue, keyParameterName, keyOptions)
        .map(_.toString)
    }

    override protected def interpret(ctx: Context): Either[InterpretationError, KeyedValue[String, T]] = interpreter(
      ctx
    )

  }

  class StringKeyOnlyMapper(
      protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
      key: LazyParameter[CharSequence],
      keyParameterName: ParameterName,
      keyOptions: KeyOptions,
  ) extends RichFlatMapFunction[Context, ValueWithContext[String]]
      with LazyParameterInterpreterFunction {

    protected implicit def toEvaluateFunctionConverterImpl: ToEvaluateFunctionConverter = toEvaluateFunctionConverter

    private lazy val interpreter = toEvaluateFunctionConverter.toEvaluateFunction(key.map(transformKey))

    protected def interpret(ctx: Context): Either[InterpretationError, String] = interpreter(ctx)

    protected def transformKey(keyValue: CharSequence): Either[InterpretationError, String] = {
      checkKey(keyValue, keyParameterName, keyOptions)
        .map(_.toString)
    }

    override def flatMap(ctx: Context, out: Collector[ValueWithContext[String]]): Unit = {
      collectHandlingErrors(ctx, out) {
        interpret(ctx) match {
          case Right(value) => ValueWithContext(value, ctx)
          case Left(InterpretationError.NullKeyError(parameterName)) =>
            throw new NullKeyException(parameterName.value)
        }
      }
    }

  }

  object KeyEnricher {

    def enrichWithKey[K, V](ctx: Context, keyedValue: KeyedValue[K, V]): Context =
      enrichWithKey(ctx, keyedValue.key)

    def enrichWithKey[K, V](ctx: Context, key: K): Context =
      ctx.withVariable(VariableConstants.KeyVariableName, key)

    def contextTransformation(ctx: ValidationContext, groupByReturnType: TypingResult)(
        implicit nodeId: NodeId
    ): ValidatedNel[ProcessCompilationError, ValidationContext] =
      ctx.withVariableOverriden(VariableConstants.KeyVariableName, groupByReturnType, None)

  }

  private[keyed] final class NullKeyException(parameterName: String)
      extends NonTransientException(
        input = "",
        message = s"A key value derived from parameter '$parameterName' evaluated to null. Keys must not be null."
      )

}
