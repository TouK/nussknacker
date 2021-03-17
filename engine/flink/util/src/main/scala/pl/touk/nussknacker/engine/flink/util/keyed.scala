package pl.touk.nussknacker.engine.flink.util

import org.apache.flink.api.common.functions.RichMapFunction
import org.apache.flink.api.common.typeinfo.TypeInformation
import org.apache.flink.api.scala._
import pl.touk.nussknacker.engine.api.{Context, LazyParameter, LazyParameterInterpreter, ValueWithContext}
import pl.touk.nussknacker.engine.flink.api.process.{FlinkLazyParameterFunctionHelper, LazyParameterInterpreterFunction}
import scala.reflect.runtime.universe.TypeTag

// Must be in object because of Java interop (problems with package object) and abstract type StringKeyedValue[V]
object keyed {

  type StringKeyedValue[V] = KeyedValue[String, V]

  case class KeyedValue[+K, +V](key: K, value: V) {

    def tupled: (K, V) = (key, value)

    def mapKey[NK](f: K => NK): KeyedValue[NK, V] =
      copy(key = f(key))

    def mapValue[NV](f: V => NV): KeyedValue[K, NV] =
      copy(value = f(value))

  }

  object KeyedValue {

    // It is helper function for interop with java - e.g. in case when you want to have KeyedEvent[POJO, POJO]
    def typeInformation[K, V](keyTypeInformation: TypeInformation[K], valueTypeInformation: TypeInformation[V]): TypeInformation[KeyedValue[K, V]] = {
      implicit val implicitKeyTypeInformation: TypeInformation[K] = keyTypeInformation
      implicit val implicitValueTypeInformation: TypeInformation[V] = valueTypeInformation
      implicitly[TypeInformation[KeyedValue[K, V]]]
    }

  }

  object StringKeyedValue {

    def apply[V](key: String, value: V): StringKeyedValue[V] = KeyedValue(key, value)

    def unapply[V](keyedValue: StringKeyedValue[V]): Option[(String, V)] = KeyedValue.unapply(keyedValue)

    // It is helper function for interop with java - e.g. in case when you want to have StringKeyedEvent[POJO]
    def typeInformation[V](valueTypeInformation: TypeInformation[V]): TypeInformation[KeyedValue[String, V]] = {
      KeyedValue.typeInformation(implicitly[TypeInformation[String]], valueTypeInformation)
    }

  }

  /* TODO, FIXME: Errors on interpret should be handled with FlinkEspExceptionHandler; see KeyedRecordFlatMapper */
  abstract class BaseKeyedValueMapper[OutputKey <: AnyRef: TypeTag, OutputValue <:AnyRef: TypeTag] extends RichMapFunction[Context, ValueWithContext[KeyedValue[OutputKey, OutputValue]]] with LazyParameterInterpreterFunction {

    protected implicit def lazyParameterInterpreterImpl: LazyParameterInterpreter = lazyParameterInterpreter

    protected def prepareInterpreter(key: LazyParameter[OutputKey], value: LazyParameter[OutputValue]): Context => KeyedValue[OutputKey, OutputValue] = {
      lazyParameterInterpreter.syncInterpretationFunction(
        key.product(value).map(tuple => KeyedValue(tuple._1, tuple._2)))
    }

    protected def interpret(ctx: Context): KeyedValue[OutputKey, OutputValue]

    override def map(ctx: Context): ValueWithContext[KeyedValue[OutputKey, OutputValue]] = ValueWithContext(interpret(ctx), ctx)

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
  class StringKeyedValueMapper[T<:AnyRef:TypeTag](protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper,
                                                  key: LazyParameter[CharSequence],
                                                  value: LazyParameterInterpreter => LazyParameter[T])
    extends BaseKeyedValueMapper[String, T] {

    def this(lazyParameterHelper: FlinkLazyParameterFunctionHelper,
        key: LazyParameter[CharSequence],
        value: LazyParameter[T]) = this(lazyParameterHelper, key, _ => value)

    private lazy val interpreter = prepareInterpreter(key.map(transformKey), value(lazyParameterInterpreter))

    protected def transformKey(keyValue: CharSequence): String = {
      Option(keyValue).map(_.toString).getOrElse("")
    }

    override protected def interpret(ctx: Context): KeyedValue[String, T] = interpreter(ctx)

  }

  class StringKeyOnlyMapper(protected val lazyParameterHelper: FlinkLazyParameterFunctionHelper, key: LazyParameter[CharSequence])
    extends RichMapFunction[Context, ValueWithContext[String]] with LazyParameterInterpreterFunction {

    protected implicit def lazyParameterInterpreterImpl: LazyParameterInterpreter = lazyParameterInterpreter

    private lazy val interpreter = lazyParameterInterpreter.syncInterpretationFunction(key.map(transformKey))

    protected def interpret(ctx: Context): String = interpreter(ctx)

    protected def transformKey(keyValue: CharSequence): String = {
      Option(keyValue).map(_.toString).getOrElse("")
    }

    override def map(ctx: Context): ValueWithContext[String] = ValueWithContext(interpret(ctx), ctx)

  }

}
