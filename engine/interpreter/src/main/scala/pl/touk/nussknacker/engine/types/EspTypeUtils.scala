package pl.touk.nussknacker.engine.types

import java.lang.reflect._

import cats.data.StateT
import cats.effect.IO
import org.apache.commons.lang3.{ClassUtils, StringUtils}
import pl.touk.nussknacker.engine.api.process.PropertyFromGetterExtractionStrategy.{AddPropertyNextToGetter, DoNothing, ReplaceGetterWithProperty}
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, PropertyFromGetterExtractionStrategy, VisibleMembersPredicate}
import pl.touk.nussknacker.engine.api.typed.typing.Typed
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, VisibleMembersPredicate}
import pl.touk.nussknacker.engine.api.typed.typing.{ClassLike, Typed, TypedClass}
import pl.touk.nussknacker.engine.api.{Documentation, ParamName}
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, MethodInfo, Parameter}
import sun.reflect.generics.reflectiveObjects.ParameterizedTypeImpl

import scala.concurrent.Future

object EspTypeUtils {

  def clazzDefinition(clazz: Class[_])
                     (implicit settings: ClassExtractionSettings): ClazzDefinition =
    ClazzDefinition(TypedClass(clazz), getPublicMethodAndFields(clazz))

  def extractParameterType(p: java.lang.reflect.Parameter, classesToExtractGenericFrom: Class[_]*): Class[_] =
    if (classesToExtractGenericFrom.contains(p.getType)) {
      val parameterizedType = p.getParameterizedType.asInstanceOf[ParameterizedType]
      parameterizedType.getActualTypeArguments.apply(0) match {
        case a: Class[_] => a
        case b: ParameterizedType => b.getRawType.asInstanceOf[Class[_]]
      }
    } else {
      p.getType
    }

  def getCompanionObject[T](klazz: Class[T]): T = {
    klazz.getField("MODULE$").get(null).asInstanceOf[T]
  }

  def getGenericType(genericReturnType: Type): Option[ClassLike] = {
    val hasGenericReturnType = genericReturnType.isInstanceOf[ParameterizedTypeImpl]
    if (hasGenericReturnType) inferGenericMonadType(genericReturnType.asInstanceOf[ParameterizedTypeImpl])
    else None
  }

  //TODO: what is *really* needed here?? is it performant enough??
  def signatureElementMatches(signatureType: Class[_], passedValueClass: Class[_]): Boolean = {
    def unbox(typ: Class[_]) = if (ClassUtils.isPrimitiveWrapper(typ)) ClassUtils.wrapperToPrimitive(typ) else typ

    ClassUtils.isAssignable(passedValueClass, signatureType, true) ||
      ClassUtils.isAssignable(unbox(passedValueClass), unbox(signatureType), true)
  }

  private def methodNames(clazz: Class[_]): List[String] = {
    clazz.getMethods.map(_.getName).toList
  }

  private def getPublicMethodAndFields(clazz: Class[_])
                                      (implicit settings: ClassExtractionSettings): Map[String, MethodInfo] = {
    val membersPredicate = settings.visibleMembersPredicate(clazz)
    val methods = publicMethods(clazz, membersPredicate)
    val fields = publicFields(clazz, membersPredicate)
    methods ++ fields
  }

  private def publicMethods(clazz: Class[_], membersPredicate: VisibleMembersPredicate)
                           (implicit settings: ClassExtractionSettings): Map[String, MethodInfo] = {
    /* From getMethods javadoc: If this {@code Class} object represents an interface then the returned array
           does not contain any implicitly declared methods from {@code Object}.
           The same for primitives - we assume that languages like SpEL will be able to do boxing
           It could be significant only for toString, as we filter out other Object methods, but to be consistent...
         */
    val additionalMethods = if (clazz.isInterface) {
      classOf[Object].getMethods.toList
    } else if (clazz.isPrimitive) {
      ClassUtils.primitiveToWrapper(clazz).getMethods.toList
    } else {
      List.empty
    }
    val publicMethods = clazz.getMethods.toList ++ additionalMethods

    val filteredMethods = publicMethods.filter(membersPredicate.shouldBeVisible)

    val methodNameAndInfoList = filteredMethods.flatMap { method =>
      methodAccessMethods(method).map(_ -> toMethodInfo(method))
    }

    val genericReturnTypeDeduplicated = deduplicateMethodsWithGenericReturnType(methodNameAndInfoList)

    val sortedByArityDesc = genericReturnTypeDeduplicated.sortBy(- _._2.parameters.size)

    // Currently SpEL methods are naively and optimistically type checked. This is, for overloaded methods with
    // different arity, validation is successful when SpEL MethodReference provides the number of parameters greater
    // or equal to method with the smallest arity.
    sortedByArityDesc.toMap
  }

  /*
    This is tricky case. If we have generic class and concrete subclasses, e.g.
    - ChronoLocalDateTime<D extends ChronoLocalDate>
    - LocalDateTime extends ChronoLocalDateTime<LocalDate>
    and method with generic return type from superclass: D toLocalDate()
    getMethods return two toLocalDate methods:
      ChronoLocalDate toLocalDate()
      LocalDate toLocalDate()
    In our case the second one is correct
   */
  private def deduplicateMethodsWithGenericReturnType(methodNameAndInfoList: List[(String, MethodInfo)]) = {
    methodNameAndInfoList.groupBy(mi => (mi._1, mi._2.parameters)).toList.map {
      case (_, methodsForParams) =>
        /*
          we want to find "most specific" class, however suprisingly it's not always possible, because we treat e.g. isLeft and left methods
          as equal (for javabean-like access) and e.g. in scala Either this is perfectly possible. In case we cannot find most specific
          class we pick arbitrary one (we sort to avoid randomness)
         */
        methodsForParams.find { case (_, MethodInfo(_, ret, _)) =>
          methodsForParams.forall(mi => Typed(ret).canBeSubclassOf(Typed(mi._2.refClazz)))
        }.getOrElse(methodsForParams.minBy(_._2.refClazz.display))
    }
  }

  private def toMethodInfo(method: Method)
    = MethodInfo(getParameters(method), getReturnClassForMethod(method), getNussknackerDocs(method))

  private def methodAccessMethods(method: Method)
                                 (implicit settings: ClassExtractionSettings): List[String] = {
    val isGetter = method.getName.matches("^(get|is).+") && method.getParameterCount == 0
    if (isGetter) {
      val propertyMethod = StringUtils.uncapitalize(method.getName.replaceAll("^get|^is", ""))
      settings.propertyExtractionStrategy match {
        case AddPropertyNextToGetter    => List(method.getName, propertyMethod)
        case ReplaceGetterWithProperty  => List(propertyMethod)
        case DoNothing                  => List(method.getName)
      }
    } else {
      List(method.getName)
    }
  }

  private def publicFields(clazz: Class[_], membersPredicate: VisibleMembersPredicate)
                          (implicit settings: ClassExtractionSettings): Map[String, MethodInfo] = {
    val interestingFields = clazz.getFields.filter(membersPredicate.shouldBeVisible)
    interestingFields.map { field =>
      field.getName -> MethodInfo(List.empty, getReturnClassForField(field), getNussknackerDocs(field))
    }.toMap
  }

  private def getReturnClassForMethod(method: Method): ClassLike = {
    getGenericType(method.getGenericReturnType).orElse(extractClass(method.getGenericReturnType)).getOrElse(TypedClass(method.getReturnType))
  }

  private def getParameters(method: Method): List[Parameter] = {
    for {
      param <- method.getParameters.toList
      annotationOption = Option(param.getAnnotation(classOf[ParamName]))
      name = annotationOption.map(_.value).getOrElse(param.getName)
      clazzRef = TypedClass(param.getType)
    } yield Parameter(name, clazzRef)
  }

  private def getNussknackerDocs(accessibleObject: AccessibleObject): Option[String] = {
    Option(accessibleObject.getAnnotation(classOf[Documentation])).map(_.description())
  }

  private def getReturnClassForField(field: Field): ClassLike = {
    getGenericType(field.getGenericType).orElse(extractClass(field.getType)).getOrElse(TypedClass(field.getType))
  }

  //TODO this is not correct for primitives and complicated hierarchies, but should work in most cases
  //http://docs.oracle.com/javase/8/docs/api/java/lang/reflect/ParameterizedType.html#getActualTypeArguments--
  private def inferGenericMonadType(genericMethodType: ParameterizedTypeImpl): Option[ClassLike] = {
    val rawType = genericMethodType.getRawType

    if (classOf[StateT[IO, _, _]].isAssignableFrom(rawType)) {
      val returnType = genericMethodType.getActualTypeArguments.apply(3) // it's IndexedStateT[IO, ContextWithLazyValuesProvider, ContextWithLazyValuesProvider, A]
      extractClass(returnType)
    }
    else if (classOf[Future[_]].isAssignableFrom(rawType)) {
      val futureGenericType = genericMethodType.getActualTypeArguments.apply(0)
      extractClass(futureGenericType)
    }
    else if (classOf[Option[_]].isAssignableFrom(rawType)) {
      val optionGenericType = genericMethodType.getActualTypeArguments.apply(0)
      extractClass(optionGenericType)
    }
    else None
  }

  private def extractClass(futureGenericType: Type): Option[ClassLike] = {
    futureGenericType match {
      case t: Class[_] => Some(TypedClass(t))
      case t: ParameterizedTypeImpl => Some(extractGenericParams(t))
      case t => None
    }
  }

  private def extractGenericParams(paramsType: ParameterizedTypeImpl): ClassLike = {
    val rawType = paramsType.getRawType
    if (classOf[java.util.Collection[_]].isAssignableFrom(rawType)) {
      TypedClass(rawType, paramsType.getActualTypeArguments.toList.flatMap(extractClass))
    } else if (classOf[scala.collection.Iterable[_]].isAssignableFrom(rawType)) {
      TypedClass(rawType, paramsType.getActualTypeArguments.toList.flatMap(extractClass))
    } else TypedClass(rawType)
  }

}
