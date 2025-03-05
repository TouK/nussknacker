package pl.touk.nussknacker.engine.spel.internal

import org.springframework.expression.{AccessException, EvaluationContext, PropertyAccessor, TypedValue}
import org.springframework.expression.spel.support.ReflectivePropertyAccessor
import org.springframework.util.ClassUtils
import pl.touk.nussknacker.engine.api.dict.DictInstance
import pl.touk.nussknacker.engine.api.exception.NonTransientException
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionSet
import pl.touk.nussknacker.engine.extension.ExtensionMethodResolver

import java.lang.reflect.{Field, Method, Modifier}
import java.util.{Collections => JCollections, Optional}
import scala.collection.concurrent.TrieMap

object propertyAccessors {

  // Order of accessors matters - property from first accessor that returns `true` from `canRead` will be chosen.
  // This general order can be overridden - each accessor can define target classes for which it will have precedence -
  // through the `getSpecificTargetClasses` method.
  def configured(
      accessChecker: MethodAccessChecker,
      set: ClassDefinitionSet
  ): Seq[PropertyAccessor] = {
    Seq(
      MapPropertyAccessor, // must be before NoParamMethodPropertyAccessor and ReflectivePropertyAccessor
      new CheckedReflectivePropertyAccessor(accessChecker),
      NullPropertyAccessor,                                 // must be before other non-standard ones
      new ScalaOptionOrNullPropertyAccessor(accessChecker), // must be before scalaPropertyAccessor
      new JavaOptionalOrNullPropertyAccessor(accessChecker),
      new PrimitiveOrWrappersPropertyAccessor(accessChecker),
      new StaticPropertyAccessor(accessChecker),
      TypedDictInstancePropertyAccessor, // must be before NoParamMethodPropertyAccessor
      new NoParamMethodPropertyAccessor(accessChecker),
      new ExtensionMethodsPropertyAccessor(accessChecker, set),
      // it can add performance overhead so it will be better to keep it on the bottom
      MapLikePropertyAccessor,
      MapMissingPropertyToNullAccessor, // must be after NoParamMethodPropertyAccessor, ExtensionMethodsPropertyAccessor
    )
  }

  class CheckedReflectivePropertyAccessor(accessChecker: MethodAccessChecker) extends ReflectivePropertyAccessor {

    override def canRead(context: EvaluationContext, target: Any, name: String): Boolean = {
      val canReadFromSupper = super.canRead(context, target, name)
      // for access check we have to keep behaviour of the method consistent
      // with the way in which ReflectivePropertyAccessor looks for member to use during read
      if (canReadFromSupper && accessChecker != AllMethodsAllowed) {
        val targetClass = computeTargetClass(target)
        if (!(targetClass.isArray && name == "length")) {
          findGetterMember(name, targetClass, target) match {
            case Some(getterMethod) =>
              accessChecker.checkAccessIfMethodFound(targetClass)(Some(getterMethod))
            case None =>
              findFieldMember(name, targetClass, target).foreach { field =>
                accessChecker.checkAccessForMethodName(targetClass, field.getName, target.isInstanceOf[Class[_]])
              }
          }
        }
      }
      canReadFromSupper
    }

    private def computeTargetClass(target: Any): Class[_] = {
      target match {
        case clazz: Class[_] => clazz
        case _               => target.getClass
      }
    }

    private def findGetterMember(propertyName: String, clazz: Class[_], target: Any): Option[Method] = {
      Option(findGetterForProperty(propertyName, clazz, target.isInstanceOf[Class[_]])) match {
        case None if target.isInstanceOf[Class[_]] =>
          Option(findGetterForProperty(propertyName, target.getClass, false))
        case opt => opt
      }
    }

    private def findFieldMember(name: String, clazz: Class[_], target: Any): Option[Field] = {
      Option(findField(name, clazz, target.isInstanceOf[Class[_]])) match {
        case None if target.isInstanceOf[Class[_]] => Option(findField(name, target.getClass, false))
        case opt                                   => opt
      }
    }

  }

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
  class NoParamMethodPropertyAccessor(accessChecker: MethodAccessChecker)
      extends ReflectivePropertyAccessor
      with ReadOnly
      with Caching {

    override def findGetterForProperty(propertyName: String, clazz: Class[_], mustBeStatic: Boolean): Method = {
      findMethodFromClass(propertyName, clazz).orNull
    }

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] =
      accessChecker.checkAccessIfMethodFound(target) {
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
  class PrimitiveOrWrappersPropertyAccessor(
      accessChecker: MethodAccessChecker
  ) extends PropertyAccessor
      with ReadOnly
      with Caching {

    override def getSpecificTargetClasses: Array[Class[_]] = null

    override protected def invokeMethod(
        propertyName: String,
        method: Method,
        target: Any,
        context: EvaluationContext
    ): Any = method.invoke(target)

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] =
      accessChecker.checkAccessIfMethodFound(target) {
        target.getMethods.find(m =>
          ClassUtils.isPrimitiveOrWrapper(target) && m.getParameterCount == 0 && m.getName == name
        )
      }

  }

  class StaticPropertyAccessor(
      accessChecker: MethodAccessChecker
  ) extends PropertyAccessor
      with ReadOnly
      with StaticMethodCaching {

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] =
      accessChecker.checkAccessIfMethodFound(target, onlyStaticMethods = true) {
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
  class ScalaOptionOrNullPropertyAccessor(accessChecker: MethodAccessChecker)
      extends PropertyAccessor
      with ReadOnly
      with Caching {

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] =
      accessChecker.checkAccessIfMethodFound(target) {
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
  class JavaOptionalOrNullPropertyAccessor(accessChecker: MethodAccessChecker)
      extends PropertyAccessor
      with ReadOnly
      with Caching {

    override protected def reallyFindMethod(name: String, target: Class[_]): Option[Method] =
      accessChecker.checkAccessIfMethodFound(target) {
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

  private class ExtensionMethodsPropertyAccessor(
      accessChecker: MethodAccessChecker,
      set: ClassDefinitionSet
  ) extends PropertyAccessor
      with ReadOnly {
    private val methodResolver = new ExtensionMethodResolver(set)

    override def getSpecificTargetClasses: Array[Class[_]] = null

    override def canRead(context: EvaluationContext, target: Any, methodName: String): Boolean = {
      methodResolver.maybeResolve(target, methodName, JCollections.emptyList()) match {
        case Some(_) =>
          accessChecker.checkAccessForMethodName(target.getClass, methodName, onlyStaticMethods = false)
          true
        case None => false
      }
    }

    override def read(context: EvaluationContext, target: Any, methodName: String): TypedValue =
      methodResolver.maybeResolve(target, methodName, JCollections.emptyList()) match {
        case Some(executor) => executor.execute(context, target, methodName)
        case None           => throw new AccessException(s"Cannot find method with name: $methodName")
      }

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

  sealed trait MethodAccessChecker {

    def checkAccessIfMethodFound(targetClass: Class[_], onlyStaticMethods: Boolean = false)(
        methodOpt: Option[Method]
    ): Option[Method] = {
      methodOpt.foreach(method => checkAccessForMethodName(targetClass, method.getName, onlyStaticMethods))
      methodOpt
    }

    def checkAccessForMethodName(
        targetClass: Class[_],
        methodName: String,
        onlyStaticMethods: Boolean
    ): Unit = {
      if (!methodAllowed(targetClass, methodName, onlyStaticMethods)) {
        throw new IllegalStateException(
          s"The $methodName method call on type ${targetClass.getSimpleName} is not allowed"
        )
      }
    }

    protected def methodAllowed(
        targetClass: Class[_],
        methodName: String,
        onlyStaticMethods: Boolean
    ): Boolean

  }

  object MethodAccessChecker {

    def create(classDefinitionSet: ClassDefinitionSet, dynamicPropertyAccessAllowed: Boolean): MethodAccessChecker = {
      if (!dynamicPropertyAccessAllowed) {
        OnlyDefinedParameterlessMethodsAllowed(classDefinitionSet)
      } else {
        AllMethodsAllowed
      }
    }

  }

  object AllMethodsAllowed extends MethodAccessChecker {

    override protected def methodAllowed(
        targetClass: Class[_],
        methodName: String,
        onlyStaticMethods: Boolean
    ): Boolean = true

  }

  final case class OnlyDefinedParameterlessMethodsAllowed(classDefinitionSet: ClassDefinitionSet)
      extends MethodAccessChecker {

    override protected def methodAllowed(
        targetClass: Class[_],
        methodName: String,
        onlyStaticMethods: Boolean
    ): Boolean = {
      classDefinitionSet.isParameterlessMethodAllowed(targetClass, methodName, onlyStaticMethods)
    }

  }

}
