package pl.touk.nussknacker.engine.spel.internal

import org.apache.commons.lang3.ClassUtils
import org.springframework.expression.spel.support.ReflectivePropertyAccessor
import org.springframework.expression.{EvaluationContext, PropertyAccessor, TypedValue}
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet

import java.lang.reflect.{Method, Modifier}
import java.util.Optional
import scala.collection.concurrent.TrieMap

object propertyAccessors {

  // Order of accessors matters - property from first accessor that returns `true` from `canRead` will be chosen.
  // This general order can be overridden - each accessor can define target classes for which it will have precedence -
  // through the `getSpecificTargetClasses` method.
  def configured(classDefinitionSet: ClassDefinitionSet): Seq[PropertyAccessor] = {

    Seq(
      MapPropertyAccessor, // must be before NoParamMethodPropertyAccessor and ReflectivePropertyAccessor
      new ReflectivePropertyAccessor(),
      NullPropertyAccessor,                                      // must be before other non-standard ones
      new ScalaOptionOrNullPropertyAccessor(classDefinitionSet), // must be before scalaPropertyAccessor
      new JavaOptionalOrNullPropertyAccessor(classDefinitionSet),
      new PrimitiveOrWrappersPropertyAccessor(classDefinitionSet),
      new StaticPropertyAccessor(classDefinitionSet),
      TypedDictInstancePropertyAccessor, // must be before NoParamMethodPropertyAccessor
      new NoParamMethodPropertyAccessor(classDefinitionSet),
      // it can add performance overhead so it will be better to keep it on the bottom
      MapLikePropertyAccessor,
      MapMissingPropertyToNullAccessor, // must be after NoParamMethodPropertyAccessor
    )
  }

  class CheckedReflectivePropertyAccessor extends ReflectivePropertyAccessor {}

  object NullPropertyAccessor extends PropertyAccessor with ReadOnly {

    override def getSpecificTargetClasses: Array[Class[_]] = null

    override def canRead(context: EvaluationContext, target: Any, name: String): Boolean = target == null

    override def read(context: EvaluationContext, target: Any, name: String): TypedValue =
      // can we extract anything else here?
      throw NonTransientException(name, s"Cannot invoke method/property $name on null object")

  }

  /* PropertyAccessor for methods without parameters - e.g. parameters in case classes
    TODO: is it ok to treat all methods without parameters as properties?
    We have to handle Primitives/Wrappers differently, as they have problems with bytecode generation (@see PrimitiveOrWrappersPropertyAccessor)

    This one is a bit tricky. We extend ReflectivePropertyAccessor, as it's the only sensible way to make it compilable,
    however it's not so easy to extend and in interpreted mode we skip original implementation
   */
  class NoParamMethodPropertyAccessor(protected val classDefinitionSet: ClassDefinitionSet)
      extends ReflectivePropertyAccessor
      with ReadOnly
      with Caching
      with ClassDefinitionSetChecking {

    override def findGetterForProperty(propertyName: String, clazz: Class[_], mustBeStatic: Boolean): Method = {
      findMethodFromClass(propertyName, clazz).orNull
    }

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] =
      checkAccessIfMethodFound(target) {
        target.getMethods.find(m =>
          !ClassUtils.isPrimitiveOrWrapper(target) && m.getParameterCount == 0 && m.getName == name
        )
      }

    override protected def invokeMethod(
        propertyName: String,
        method: Method,
        target: Any,
        context: EvaluationContext
    ): AnyRef = {
      // warning: the method is not called in case of access via optimized ReflectivePropertyAccessor!!!
      method.invoke(target)
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  // Spring bytecode generation fails when we try to invoke methods on primitives, so we
  // *do not* extend ReflectivePropertyAccessor and we force interpreted mode
  // TODO: figure out how to make bytecode generation work also in this case
  class PrimitiveOrWrappersPropertyAccessor(protected val classDefinitionSet: ClassDefinitionSet)
      extends PropertyAccessor
      with ReadOnly
      with Caching
      with ClassDefinitionSetChecking {

    override def getSpecificTargetClasses: Array[Class[_]] = null

    override protected def invokeMethod(
        propertyName: String,
        method: Method,
        target: Any,
        context: EvaluationContext
    ): Any = method.invoke(target)

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] =
      checkAccessIfMethodFound(target) {
        target.getMethods.find(m =>
          ClassUtils.isPrimitiveOrWrapper(target) && m.getParameterCount == 0 && m.getName == name
        )
      }

  }

  class StaticPropertyAccessor(protected val classDefinitionSet: ClassDefinitionSet)
      extends PropertyAccessor
      with ReadOnly
      with StaticMethodCaching
      with ClassDefinitionSetChecking {

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] =
      checkAccessIfMethodFound(target) {
        target
          .asInstanceOf[Class[_]]
          .getMethods
          .find(m => m.getParameterCount == 0 && m.getName == name && Modifier.isStatic(m.getModifiers))
      }

    override protected def invokeMethod(
        propertyName: String,
        method: Method,
        target: Any,
        context: EvaluationContext
    ): Any = {
      method.invoke(target)
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  // TODO: handle methods with multiple args or at least validate that they can't be called
  //       - see test for similar case for Futures: "usage of methods with some argument returning future"
  class ScalaOptionOrNullPropertyAccessor(protected val classDefinitionSet: ClassDefinitionSet)
      extends PropertyAccessor
      with ReadOnly
      with Caching
      with ClassDefinitionSetChecking {

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] =
      checkAccessIfMethodFound(target) {
        target.getMethods.find(m =>
          m.getParameterCount == 0 && m.getName == name && classOf[Option[_]].isAssignableFrom(m.getReturnType)
        )
      }

    override protected def invokeMethod(
        propertyName: String,
        method: Method,
        target: Any,
        context: EvaluationContext
    ): Any = {
      method.invoke(target).asInstanceOf[Option[Any]].orNull
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  // TODO: handle methods with multiple args or at least validate that they can't be called
  //       - see test for similar case for Futures: "usage of methods with some argument returning future"
  class JavaOptionalOrNullPropertyAccessor(protected val classDefinitionSet: ClassDefinitionSet)
      extends PropertyAccessor
      with ReadOnly
      with Caching
      with ClassDefinitionSetChecking {

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] =
      checkAccessIfMethodFound(target) {
        target.getMethods.find(m =>
          m.getParameterCount == 0 && m.getName == name && classOf[Optional[_]].isAssignableFrom(m.getReturnType)
        )
      }

    override protected def invokeMethod(
        propertyName: String,
        method: Method,
        target: Any,
        context: EvaluationContext
    ): Any = {
      method.invoke(target).asInstanceOf[Optional[Any]].orElse(null)
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  object MapPropertyAccessor extends PropertyAccessor with ReadOnly {

    // if map does not contain property this should return false so that `NoParamMethodPropertyAccessor` can be used
    override def canRead(context: EvaluationContext, target: scala.Any, name: String): Boolean = target match {
      case map: java.util.Map[_, _] => map.containsKey(name)
      case _                        => false
    }

    override def read(context: EvaluationContext, target: scala.Any, name: String) =
      new TypedValue(target.asInstanceOf[java.util.Map[_, _]].get(name))

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  // This accessor handles situation where a Map has no property and no no-parameter method with given name. In that
  // case we want to return null instead of throwing exception.
  object MapMissingPropertyToNullAccessor extends PropertyAccessor with ReadOnly {

    override def canRead(context: EvaluationContext, target: Any, name: String): Boolean = target match {
      case map: java.util.Map[_, _] => !map.containsKey(name)
      case _                        => false
    }

    override def read(context: EvaluationContext, target: Any, name: String): TypedValue = {
      // SpEL caches property accessors, so if first map is an empty map, for every subsequent maps evaluation will return null for any field
      // The undocumented contract between PropertyOrFieldReference and PropertyAccessor is to throw an exception in this situation
      // See "This is OK - it may have gone stale due to a class change..." comment in the PropertyOrFieldReference
      if (!canRead(context, target, name)) {
        throw new Exception("Using MapMissingPropertyToNullAccessor on target that accessor can be used with")
      }
      new TypedValue(null)
    }

    override def getSpecificTargetClasses: Array[Class[_]] = null
  }

  object TypedDictInstancePropertyAccessor extends PropertyAccessor with ReadOnly {
    // in theory this always happends, because we typed it properly ;)
    override def canRead(context: EvaluationContext, target: scala.Any, key: String) =
      true

    // we already replaced dict's label with keys so we can just return value based on key
    override def read(context: EvaluationContext, target: scala.Any, key: String) =
      new TypedValue(target.asInstanceOf[DictInstance].value(key))

    override def getSpecificTargetClasses: Array[Class[_]] = Array(classOf[DictInstance])
  }

  // mainly for Avro's GenericRecord and Table API's Row purpose
  object MapLikePropertyAccessor extends PropertyAccessor with Caching with ReadOnly {

    override protected def invokeMethod(
        propertyName: String,
        method: Method,
        target: Any,
        context: EvaluationContext
    ): Any = {
      method.invoke(target, propertyName)
    }

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] = {
      target.getMethods.find(m =>
        (m.getName == "get" || m.getName == "getField") &&
          (m.getParameterTypes sameElements Array(classOf[String]))
      )
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

    override protected def extractClassFromTarget(target: Any): Option[Class[_]] =
      Option(target).map(_.asInstanceOf[Class[_]])
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
      // this should *not* happen as we have NullPropertyAccessor
      val targetClass =
        extractClassFromTarget(target).getOrElse(throw new IllegalArgumentException(s"Null target for $name"))
      findMethodFromClass(name, targetClass)
    }

    protected def findMethodFromClass(name: String, targetClass: Class[_]): Option[Method] = {
      methodsCache.getOrElseUpdate((name, targetClass), reallyFindMethod(name, targetClass))
    }

    protected def extractClassFromTarget(target: Any): Option[Class[_]]
    protected def invokeMethod(propertyName: String, method: Method, target: Any, context: EvaluationContext): Any
    protected def reallyFindMethod(name: String, target: Class[_]): Option[Method]
  }

  trait ReadOnly { self: PropertyAccessor =>

    override def write(context: EvaluationContext, target: scala.Any, name: String, newValue: scala.Any) =
      throw new IllegalAccessException("Property is not writeable")

    override def canWrite(context: EvaluationContext, target: scala.Any, name: String) = false

  }

  trait ClassDefinitionSetChecking { self: PropertyAccessor =>

    protected def classDefinitionSet: ClassDefinitionSet

    protected def checkAccessIfMethodFound(targetClass: Class[_])(methodOpt: Option[Method]): Option[Method] = {
      methodOpt match {
        case Some(method) =>
          throwIfMethodNotInDefinitionSet(method.getName, targetClass)
          Some(method)
        case None => None
      }
    }

    private def throwIfMethodNotInDefinitionSet(method: String, targetClass: Class[_]): Unit = {
      if (!classDefinitionSet.isParameterlessMethodAllowed(targetClass, method)) {
        throw new IllegalStateException(
          s"The $method method call on type ${targetClass.getSimpleName} is not allowed"
        )
      }
    }

  }

}
