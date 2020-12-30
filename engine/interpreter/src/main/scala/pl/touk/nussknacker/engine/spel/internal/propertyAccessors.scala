package pl.touk.nussknacker.engine.spel.internal

import java.lang.reflect.{Method, Modifier}
import java.util.Optional
import org.apache.commons.lang3.ClassUtils
import org.springframework.expression.spel.support.ReflectivePropertyAccessor
import org.springframework.expression.{EvaluationContext, PropertyAccessor, TypedValue}
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.api.typed.TypedMap

import scala.collection.concurrent.TrieMap
import scala.concurrent.duration._

object propertyAccessors {

  def configured(): Seq[PropertyAccessor] = {
    //FIXME: configurable timeout...
    val lazyValuesTimeout = 1 minute

    Seq(
      new ReflectivePropertyAccessor(),
      NullPropertyAccessor, //must come before other non-standard ones
      ScalaOptionOrNullPropertyAccessor, // must be before scalaPropertyAccessor
      JavaOptionalOrNullPropertyAccessor,
      NoParamMethodPropertyAccessor,
      PrimitiveOrWrappersPropertyAccessor,
      StaticPropertyAccessor,
      MapPropertyAccessor,
      TypedDictInstancePropertyAccessor,
      // it can add performance overhead so it will be better to keep it on the bottom
      MapLikePropertyAccessor
    )
  }

  object NullPropertyAccessor extends PropertyAccessor with ReadOnly {

    override def getSpecificTargetClasses: Array[Class[_]] = null

    override def canRead(context: EvaluationContext, target: Any, name: String): Boolean = target == null

    override def read(context: EvaluationContext, target: Any, name: String): TypedValue =
      //can we extract anything else here?
      throw NonTransientException(name, s"Cannot invoke method/property $name on null object")
  }

  /* PropertyAccessor for methods without parameters - e.g. parameters in case classes
    TODO: is it ok to treat all methods without parameters as properties?
    We have to handle Primitives/Wrappers differently, as they have problems with bytecode generation (@see PrimitiveOrWrappersPropertyAccessor)

    This one is a bit tricky. We extend ReflectivePropertyAccessor, as it's the only sensible way to make it compilable,
    however it's not so easy to extend and in interpreted mode we skip original implementation
   */
  object NoParamMethodPropertyAccessor extends ReflectivePropertyAccessor with ReadOnly with Caching {

    override def findGetterForProperty(propertyName: String, clazz: Class[_], mustBeStatic: Boolean): Method = {
      findMethodFromClass(propertyName, clazz).orNull
    }

    override protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method] = {
      target.getMethods.find(m => !ClassUtils.isPrimitiveOrWrapper(target) && m.getParameterCount == 0 && m.getName == name)
    }

    override protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext): AnyRef = {
      method.invoke(target)
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  //Spring bytecode generation fails when we try to invoke methods on primitives, so we
  //*do not* extend ReflectivePropertyAccessor and we force interpreted mode
  //TODO: figure out how to make bytecode generation work also in this case
  object PrimitiveOrWrappersPropertyAccessor extends PropertyAccessor with ReadOnly with Caching {

    override def getSpecificTargetClasses: Array[Class[_]] = null

    override protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext): Any
      = method.invoke(target)

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] = {
      target.getMethods.find(m => ClassUtils.isPrimitiveOrWrapper(target) && m.getParameterCount == 0 && m.getName == name)
    }
  }

  object StaticPropertyAccessor extends PropertyAccessor with ReadOnly with StaticMethodCaching {

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] = {
      target.asInstanceOf[Class[_]].getMethods.find(m =>
        m.getParameterCount == 0 && m.getName == name && Modifier.isStatic(m.getModifiers)
      )
    }

    override protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext): Any = {
      method.invoke(target)
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  // TODO: handle methods with multiple args or at least validate that they can't be called
  //       - see test for similar case for Futures: "usage of methods with some argument returning future"
  object ScalaOptionOrNullPropertyAccessor extends PropertyAccessor with ReadOnly with Caching {

    override protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method] = {
      target.getMethods.find(m => m.getParameterCount == 0 && m.getName == name && classOf[Option[_]].isAssignableFrom(m.getReturnType))
    }

    override protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext): Any = {
      method.invoke(target).asInstanceOf[Option[Any]].orNull
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  // TODO: handle methods with multiple args or at least validate that they can't be called
  //       - see test for similar case for Futures: "usage of methods with some argument returning future"
  object JavaOptionalOrNullPropertyAccessor extends PropertyAccessor with ReadOnly with Caching {

    override protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method] = {
      target.getMethods.find(m => m.getParameterCount == 0 && m.getName == name && classOf[Optional[_]].isAssignableFrom(m.getReturnType))
    }

    override protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext): Any = {
      method.invoke(target).asInstanceOf[Optional[Any]].orElse(null)
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  object MapPropertyAccessor extends PropertyAccessor with ReadOnly {

    // For normal Maps, we always return true to have the same behaviour for missing key nad null value
    // For TypedMaps, we want to distinguish both cases and in first one, throw an exception
    override def canRead(context: EvaluationContext, target: scala.Any, name: String): Boolean =
      !target.isInstanceOf[TypedMap] || target.asInstanceOf[TypedMap].containsKey(name)

    override def read(context: EvaluationContext, target: scala.Any, name: String) =
      new TypedValue(target.asInstanceOf[java.util.Map[_, _]].get(name))

    override def getSpecificTargetClasses: Array[Class[_]] = Array(classOf[java.util.Map[_, _]])
  }

  object TypedDictInstancePropertyAccessor extends PropertyAccessor with ReadOnly {
    //in theory this always happends, because we typed it properly ;)
    override def canRead(context: EvaluationContext, target: scala.Any, key: String) =
      true

    // we already replaced dict's label with keys so we can just return value based on key
    override def read(context: EvaluationContext, target: scala.Any, key: String) =
      new TypedValue(target.asInstanceOf[DictInstance].value(key))

    override def getSpecificTargetClasses: Array[Class[_]] = Array(classOf[DictInstance])
  }

  // mainly for avro's GenericRecord purpose
  object MapLikePropertyAccessor extends PropertyAccessor with Caching with ReadOnly {

    override protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext): Any = {
      method.invoke(target, propertyName)
    }

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] = {
      target.getMethods.find(m => m.getName == "get" && (m.getParameterTypes sameElements Array(classOf[String])))
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  trait Caching extends CachingBase { self: PropertyAccessor =>

    override def canRead(context: EvaluationContext, target: scala.Any, name: String): Boolean =
      !target.isInstanceOf[Class[_]] && findMethod(name, target).isDefined

    override protected def extractClassFromTarget(target: Any): Option[Class[_]] =
      Option(target).map(_.getClass)
  }

  trait StaticMethodCaching extends CachingBase { self: PropertyAccessor =>
    override def canRead(context: EvaluationContext, target: scala.Any, name: String): Boolean =
      target.isInstanceOf[Class[_]] && findMethod(name, target).isDefined

    override protected def extractClassFromTarget(target: Any): Option[Class[_]] = Option(target).map(_.asInstanceOf[Class[_]])
  }

  trait CachingBase { self: PropertyAccessor =>
    private val methodsCache = new TrieMap[(String, Class[_]), Option[Method]]()

    override def read(context: EvaluationContext, target: scala.Any, name: String): TypedValue =
      findMethod(name, target)
        .map { method =>
          new TypedValue(invokeMethod(name, method, target, context))
        }
        .getOrElse(throw new IllegalAccessException("Property is not readable"))

    protected def findMethod(name: String, target: Any): Option[Method] = {
      //this should *not* happen as we have NullPropertyAccessor
      val targetClass = extractClassFromTarget(target).getOrElse(throw new IllegalArgumentException(s"Null target for $name"))
      findMethodFromClass(name, targetClass)
    }

    protected def findMethodFromClass(name: String, targetClass: Class[_]): Option[Method] = {
      methodsCache.getOrElseUpdate((name, targetClass), reallyFindMethod(name, targetClass))
    }


    protected def extractClassFromTarget(target: Any): Option[Class[_]]
    protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext): Any
    protected def reallyFindMethod(name: String, target: Class[_]) : Option[Method]
  }

  trait ReadOnly { self: PropertyAccessor =>

    override def write(context: EvaluationContext, target: scala.Any, name: String, newValue: scala.Any) =
      throw new IllegalAccessException("Property is not writeable")

    override def canWrite(context: EvaluationContext, target: scala.Any, name: String) = false

  }

}
