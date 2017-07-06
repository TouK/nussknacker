package pl.touk.esp.engine.types

import java.lang.reflect.{Method, ParameterizedType, Type}

import cats.Eval
import cats.data.StateT
import org.apache.commons.lang3.ClassUtils
import pl.touk.esp.engine.api.ParamName
import pl.touk.esp.engine.definition.DefinitionExtractor.{ClazzRef, PlainClazzDefinition}
import pl.touk.esp.engine.util.ThreadUtils
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl

import scala.concurrent.Future

object EspTypeUtils {

  private object ScalaCaseClassStub {
    case class DumpCaseClass()
    object DumpCaseClass
  }
  private val blackilistedMethods: Set[String] = {
    (methodNames(classOf[ScalaCaseClassStub.DumpCaseClass]) ++
      methodNames(ScalaCaseClassStub.DumpCaseClass.getClass)).toSet
  }

  private val baseClazzPackagePrefix = Set("java", "scala")

  private val blacklistedClazzPackagePrefix = Set(
    "scala.collection", "scala.Function", "scala.xml",
    "javax.xml", "java.util",
    "cats", "argonaut", "dispatch",
    "org.apache.flink.api.common.typeinfo.TypeInformation"
  )

  private val primitiveTypesToBoxed : Map[Class[_], Class[_]] = Map(
    Void.TYPE -> classOf[Void],
    java.lang.Boolean.TYPE -> classOf[java.lang.Boolean],
    java.lang.Integer.TYPE -> classOf[java.lang.Integer],
    java.lang.Long.TYPE -> classOf[java.lang.Long],
    java.lang.Float.TYPE -> classOf[java.lang.Float],
    java.lang.Double.TYPE -> classOf[java.lang.Double],
    java.lang.Byte.TYPE -> classOf[java.lang.Byte],
    java.lang.Short.TYPE -> classOf[java.lang.Short],
    java.lang.Character.TYPE -> classOf[java.lang.Character]
  )

  private val boxedToPrimitives = primitiveTypesToBoxed.map(_.swap)


  private val primitiveTypesSimpleNames = primitiveTypesToBoxed.keys.map(_.getName).toSet

  private def methodNames(clazz: Class[_]): List[String] = {
    clazz.getMethods.map(_.getName).toList
  }

  def clazzAndItsChildrenDefinition(clazzes: List[Class[_]]): List[PlainClazzDefinition] = {
    clazzes.flatMap(clazzAndItsChildrenDefinition).distinct
  }

  def clazzAndItsChildrenDefinition(clazz: Class[_]): List[PlainClazzDefinition] = {
    val result = if (clazz.isPrimitive || baseClazzPackagePrefix.exists(clazz.getName.startsWith)) {
      List(clazzDefinition(clazz))
    } else {
      val mainClazzDefinition = clazzDefinition(clazz)
      val recursiveClazzes = mainClazzDefinition.methods.values.toList
        .filter(m => !primitiveTypesSimpleNames.contains(m.refClazzName) && m.refClazzName != clazz.getName)
        .filter(m => !blacklistedClazzPackagePrefix.exists(m.refClazzName.startsWith))
        .filter(m => !m.refClazzName.startsWith("["))
        .map(_.refClazzName).distinct
        .flatMap(m => clazzAndItsChildrenDefinition(ThreadUtils.loadUsingContextLoader(m)))
      mainClazzDefinition :: recursiveClazzes
    }
    result.distinct
  }

  private def clazzDefinition(clazz: Class[_]): PlainClazzDefinition = {
    PlainClazzDefinition(ClazzRef(clazz), getDeclaredMethods(clazz))
  }

  private def getDeclaredMethods(clazz: Class[_]): Map[String, ClazzRef] = {
    val interestingMethods = clazz.getMethods.filter( m =>
      !blackilistedMethods.contains(m.getName) && !m.getName.contains("$")
    )
    val res = interestingMethods.map { method =>
      method.getName -> ClazzRef(getReturnClassForMethod(method))
    }.toMap
    res
  }

  def findParameterByParameterName(method: Method, paramName: String) =
    method.getParameters.find { p =>
      Option(p.getAnnotation(classOf[ParamName])).exists(_.value() == paramName)
    }

  def extractParameterType(p: java.lang.reflect.Parameter, classesToExtractGenericFrom: Class[_]*) =
    if (classesToExtractGenericFrom.contains(p.getType)) {
      val parameterizedType = p.getParameterizedType.asInstanceOf[ParameterizedType]
      parameterizedType.getActualTypeArguments.apply(0) match {
        case a:Class[_] => a
        case b:ParameterizedType => b.getRawType.asInstanceOf[Class[_]]
      }
    } else {
      p.getType
    }

  def getCompanionObject[T](klazz: Class[T]) : T= {
    klazz.getField("MODULE$").get(null).asInstanceOf[T]
  }

  def getReturnClassForMethod(method: Method)
    = getGenericMethodType(method).getOrElse(method.getReturnType)

  def getGenericMethodType(m: Method): Option[Class[_]] = {
    val genericReturnType = m.getGenericReturnType
    val hasGenericReturnType = genericReturnType.isInstanceOf[ParameterizedTypeImpl]
    if (hasGenericReturnType) inferGenericMonadType(genericReturnType)
    else None
  }

  //TODO this is not correct for primitives and complicated hierarchies, but should work in most cases
  //http://docs.oracle.com/javase/8/docs/api/java/lang/reflect/ParameterizedType.html#getActualTypeArguments--
  private def inferGenericMonadType(genericReturnType: Type): Option[Class[_]] = {
    val genericMethodType = genericReturnType.asInstanceOf[ParameterizedTypeImpl]
    if (classOf[StateT[Eval, _, _]].isAssignableFrom(genericMethodType.getRawType)) {
      val returnType = genericMethodType.getActualTypeArguments.apply(2) // bo StateT[Eval, S, A]
      extractClass(returnType)
    }
    else if (classOf[Future[_]].isAssignableFrom(genericMethodType.getRawType)) {
      val futureGenericType = genericMethodType.getActualTypeArguments.apply(0)
      extractClass(futureGenericType)
    }
    else if (classOf[Option[_]].isAssignableFrom(genericMethodType.getRawType)) {
      val optionGenericType = genericMethodType.getActualTypeArguments.apply(0)
      extractClass(optionGenericType)
    }
    else None
  }

  private def extractClass(futureGenericType: Type): Option[Class[_]] = {
    futureGenericType match {
      case t: Class[_] => Some(t)
      case t: ParameterizedTypeImpl => Some(t.getRawType)
      case t => None
    }
  }

  private def tryToUnBox(clazz : Class[_]) = boxedToPrimitives.getOrElse(clazz, clazz)

  //TODO: what is *really* needed here?? is it performant enough??
  def signatureElementMatches(signatureType: Class[_], passedValueClass: Class[_]) : Boolean = {
    ClassUtils.isAssignable(passedValueClass, signatureType, true) ||
      ClassUtils.isAssignable(tryToUnBox(passedValueClass), tryToUnBox(signatureType), true)
  }

}
