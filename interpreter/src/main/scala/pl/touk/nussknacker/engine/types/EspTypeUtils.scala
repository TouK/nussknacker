package pl.touk.nussknacker.engine.types

import java.lang.reflect._
import java.util.Optional
import cats.data.StateT
import cats.effect.IO
import org.apache.commons.lang3.{ClassUtils, StringUtils}
import pl.touk.nussknacker.engine.api.generics.{GenericType, Parameter, ParameterList, TypingFunction}
import pl.touk.nussknacker.engine.api.process.PropertyFromGetterExtractionStrategy.{AddPropertyNextToGetter, DoNothing, ReplaceGetterWithProperty}
import pl.touk.nussknacker.engine.api.process.{ClassExtractionSettings, VisibleMembersPredicate}
import pl.touk.nussknacker.engine.api.typed.typing.{SingleTypingResult, Typed, TypedNull, TypedUnion, TypingResult, Unknown}
import pl.touk.nussknacker.engine.api.{Documentation, ParamName}
import pl.touk.nussknacker.engine.definition.TypeInfos.{ClazzDefinition, FunctionalMethodInfo, MethodInfo, StaticMethodInfo}

import java.lang.annotation.Annotation

object EspTypeUtils {

  import pl.touk.nussknacker.engine.util.Implicits._

  def clazzDefinition(clazz: Class[_])
                     (implicit settings: ClassExtractionSettings): ClazzDefinition =
    ClazzDefinition(
      Typed.typedClass(clazz),
      extractPublicMethodsAndFields(clazz, staticMethodsAndFields = false),
      extractPublicMethodsAndFields(clazz, staticMethodsAndFields = true)
    )

  private def extractPublicMethodsAndFields(clazz: Class[_], staticMethodsAndFields: Boolean)
                                           (implicit settings: ClassExtractionSettings): Map[String, List[MethodInfo]] = {
    val membersPredicate = settings.visibleMembersPredicate(clazz)
    val methods = extractPublicMethods(clazz, membersPredicate, staticMethodsAndFields)
    val fields = extractPublicFields(clazz, membersPredicate, staticMethodsAndFields).mapValuesNow(List(_))
    filterHiddenParameterAndReturnType(methods ++ fields)
  }

  private def extractPublicMethods(clazz: Class[_], membersPredicate: VisibleMembersPredicate, staticMethodsAndFields: Boolean)
                                  (implicit settings: ClassExtractionSettings): Map[String, List[MethodInfo]] = {
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

    val methods =
      if (staticMethodsAndFields) publicMethods.filter(membersPredicate.shouldBeVisible).filter(m => Modifier.isStatic(m.getModifiers))
      else publicMethods.filter(membersPredicate.shouldBeVisible).filter(m => !Modifier.isStatic(m.getModifiers))

    // "varargs" annotation generates two methods - one with scala style varArgs
    // and one with java style varargs. We want only the second one so we have
    // to filter them.
    val filteredMethods = methods.filter(extractJavaVersionOfVarArgMethod(_).isEmpty)

    val methodNameAndInfoList = filteredMethods
      .flatMap(extractMethod(_))

    deduplicateMethodsWithGenericReturnType(methodNameAndInfoList)
  }

  //We have to filter here, not in ClassExtractionSettings, as we do e.g. boxed/unboxed mapping on TypedClass level...
  private def filterHiddenParameterAndReturnType(infos: Map[String, List[MethodInfo]])
                                                (implicit settings: ClassExtractionSettings): Map[String, List[MethodInfo]] = {
    def typeResultVisible(t: TypingResult): Boolean = t match {
      case str: SingleTypingResult =>
        !settings.isHidden(str.objType.klass) && str.objType.params.forall(typeResultVisible)
      case TypedUnion(ts) => ts.forall(typeResultVisible)
      case TypedNull => true
      case Unknown => true
    }
    def filterOneMethod(methodInfo: MethodInfo): Boolean = {
      val noVarArgs = methodInfo.staticParameters.noVarArgs.map(_.refClazz)
      val varArgs = methodInfo.staticParameters.varArg.toList.map(_.refClazz)
      val result = methodInfo.staticResult :: Nil
      (noVarArgs ::: varArgs ::: result).forall(typeResultVisible)
    }
    infos.mapValuesNow(methodList => methodList.filter(filterOneMethod)).filter(_._2.nonEmpty)
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
    val groupedByNameAndParameters = methodNameAndInfoList.groupBy(mi => (mi._1, mi._2.staticParameters))
    groupedByNameAndParameters.toList.map {
      case (_, methodsForParams) =>
        /*
          we want to find "most specific" class, however surprisingly it's not always possible, because we treat e.g. isLeft and left methods
          as equal (for javabean-like access) and e.g. in scala Either this is perfectly possible. In case we cannot find most specific
          class we pick arbitrary one (we sort to avoid randomness)
         */

        methodsForParams.find { case (_, methodInfo) =>
          methodsForParams.forall(mi => methodInfo.staticResult.canBeSubclassOf(mi._2.staticResult))
        }.getOrElse(methodsForParams.minBy(_._2.staticResult.display))
    }.toGroupedMap
      //we sort only to avoid randomness
      .mapValuesNow(_.sortBy(_.toString))
  }

  // SpEL is able to access getters using property name so you can write `obj.foo` instead of `obj.getFoo`
  private def collectMethodNames(method: Method)
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

  private def extractMethod(method: Method)
                           (implicit settings: ClassExtractionSettings): List[(String, MethodInfo)] =
    extractAnnotation(method, classOf[GenericType]) match {
      case None => extractRegularMethod(method)
      case Some(annotation) => extractGenericMethod(method, annotation)
    }

  private def getTypeFunctionInstanceFromAnnotation(method: Method, genericType: GenericType): TypingFunction = {
    val typeFunctionClass = genericType.typingFunction()
    try {
      val typeFunctionConstructor = typeFunctionClass.getDeclaredConstructor()
      typeFunctionConstructor.newInstance()
    } catch {
      case e: InstantiationException =>
        throw new IllegalArgumentException(s"TypingFunction for ${method.getName} cannot be abstract class.", e)
      case e: InvocationTargetException =>
        throw new IllegalArgumentException(s"TypingFunction's constructor for ${method.getName} failed.", e)
      case e: NoSuchMethodException =>
        throw new IllegalArgumentException(s"Could not find parameterless constructor for method ${method.getName} or its TypingFunction was declared inside non-static class.", e)
      case e: Exception =>
        throw new IllegalArgumentException(s"Could not extract information about generic method ${method.getName}.", e)
    }
  }

  private def extractGenericMethod(method: Method, genericType: GenericType)
                                  (implicit settings: ClassExtractionSettings): List[(String, MethodInfo)] = {
    val typeFunctionInstance = getTypeFunctionInstanceFromAnnotation(method, genericType)

    val parameterInfo = extractGenericParameters(typeFunctionInstance, method)
    val resultInfo = typeFunctionInstance.staticResult()
      .getOrElse(extractMethodReturnType(method))

    collectMethodNames(method).map(methodName => methodName -> FunctionalMethodInfo(
      x => typeFunctionInstance.computeResultType(x),
      parameterInfo,
      resultInfo,
      methodName,
      extractNussknackerDocs(method)
    ))
  }

  private def extractRegularMethod(method: Method)
                                  (implicit settings: ClassExtractionSettings): List[(String, StaticMethodInfo)] =
    collectMethodNames(method).map(methodName => methodName -> StaticMethodInfo(
      ParameterList.fromList(extractParameters(method), method.isVarArgs),
      extractMethodReturnType(method),
      methodName,
      extractNussknackerDocs(method)
    ))

  private def extractPublicFields(clazz: Class[_], membersPredicate: VisibleMembersPredicate, staticMethodsAndFields: Boolean)
                                 (implicit settings: ClassExtractionSettings): Map[String, StaticMethodInfo] = {
    val interestingFields = clazz.getFields.filter(membersPredicate.shouldBeVisible)
    val fields =
      if(staticMethodsAndFields) interestingFields.filter(m => Modifier.isStatic(m.getModifiers))
      else interestingFields.filter(m => !Modifier.isStatic(m.getModifiers))
    fields.map { field =>
      field.getName -> StaticMethodInfo(
        ParameterList(Nil, None),
        extractFieldReturnType(field),
        field.getName,
        extractNussknackerDocs(field)
      )
    }.toMap
  }

  private def extractNussknackerDocs(accessibleObject: AccessibleObject): Option[String] = {
    extractAnnotation(accessibleObject, classOf[Documentation]).map(_.description())
  }

  private def checkSubclassParameters(subclassParameters: List[Parameter],
                                      subclassIsVarArg: Boolean,
                                      superclassParameters: List[Parameter],
                                      superclassIsVarArg: Boolean): Boolean = {
    val (subclassNoVarArg, subclassVarArgOption) = MethodInfo.separateVarArg(subclassParameters, subclassIsVarArg)
    val (superclassNoVarArg, superclassVarArgOption) = MethodInfo.separateVarArg(superclassParameters, superclassIsVarArg)

    val subclassNoVarArgOption = subclassNoVarArg.map(Some(_))
    val superclassNoVarArgOption = superclassNoVarArg.map(Some(_))

    val zippedParameters = subclassNoVarArgOption.zipAll(superclassNoVarArgOption, subclassVarArgOption, superclassVarArgOption) :+
      (subclassVarArgOption, superclassVarArgOption)

    subclassNoVarArg.length >= superclassNoVarArg.length && zippedParameters.forall{
      case (None, None) => true
      case (None, Some(_)) => true
      case (Some(_), None) => false
      case (Some(sub), Some(sup)) => sub.refClazz.canBeSubclassOf(sup.refClazz)
    }
  }

  private def extractGenericParameters(typingFunction: TypingFunction, method: Method): List[Parameter] = {
    def autoExtractedParameters = extractParameters(method)
    def definedParametersOption = typingFunction.staticParameters().map(_.map{ case (name, typ) => Parameter(name, typ) })

    definedParametersOption match {
      case Some(definedParameters) =>
        if (!checkSubclassParameters(definedParameters, subclassIsVarArg = false, autoExtractedParameters, superclassIsVarArg = method.isVarArgs))
          throw new IllegalArgumentException(s"Generic function ${method.getName} has declared parameters that are incompatible with methods signature")
        definedParameters
      case None =>
        autoExtractedParameters
    }
  }

  private def extractParameters(method: Method): List[Parameter] = {
    for {
      param <- method.getParameters.toList
      annotationOption = extractAnnotation(param, classOf[ParamName])
      name = annotationOption.map(_.value).getOrElse(param.getName)
      paramType = extractParameterType(param)
    } yield Parameter(name, paramType)
  }

  def extractParameterType(javaParam: java.lang.reflect.Parameter): TypingResult = {
    extractClass(javaParam.getParameterizedType).getOrElse(Typed(javaParam.getType))
  }

  private def extractFieldReturnType(field: Field): TypingResult = {
    extractGenericReturnType(field.getGenericType).orElse(extractClass(field.getGenericType)).getOrElse(Typed(field.getType))
  }

  def extractMethodReturnType(method: Method): TypingResult = {
    extractGenericReturnType(method.getGenericReturnType).orElse(extractClass(method.getGenericReturnType)).getOrElse(Typed(method.getReturnType))
  }

  private def extractGenericReturnType(typ: Type): Option[TypingResult] = {
    typ match {
      case t: ParameterizedType if t.getRawType.isInstanceOf[Class[_]] => extractGenericMonadReturnType(t, t.getRawType.asInstanceOf[Class[_]])
      case _ => None
    }
  }

  // This method should be used only for method's and field's return type - for method's parameters such unwrapping has no sense
  //
  // Arguments of generic types that are Scala's primitive types are always erased by Scala compiler to java.lang.Object:
  // * issue: https://github.com/scala/bug/issues/4214 (and discussion at https://groups.google.com/g/scala-internals/c/K2dELqajQbg/m/gV0tbjRHJ4UJ)
  // * commit: https://github.com/scala/scala/commit/e42733e9fe1f3af591976fbb48b66035253d85b9
  private def extractGenericMonadReturnType(genericReturnType: ParameterizedType, genericReturnRawType: Class[_]): Option[TypingResult] = {
    // see ScalaLazyPropertyAccessor
    if (classOf[StateT[IO, _, _]].isAssignableFrom(genericReturnRawType)) {
      val returnType = genericReturnType.getActualTypeArguments.apply(3) // it's IndexedStateT[IO, ContextWithLazyValuesProvider, ContextWithLazyValuesProvider, A]
      extractClass(returnType)
    }
    // see ScalaOptionOrNullPropertyAccessor
    else if (classOf[Option[_]].isAssignableFrom(genericReturnRawType)) {
      val optionGenericType = genericReturnType.getActualTypeArguments.apply(0)
      extractClass(optionGenericType)
    }
    // see JavaOptionalOrNullPropertyAccessor
    else if (classOf[Optional[_]].isAssignableFrom(genericReturnRawType)) {
      val optionalGenericType = genericReturnType.getActualTypeArguments.apply(0)
      extractClass(optionalGenericType)
    }
    else None
  }

  //TODO this is not correct for primitives and complicated hierarchies, but should work in most cases
  //http://docs.oracle.com/javase/8/docs/api/java/lang/reflect/ParameterizedType.html#getActualTypeArguments--
  private def extractClass(typ: Type): Option[TypingResult] = {
    typ match {
      case t: Class[_] => Some(Typed(t))
      case t: ParameterizedType if t.getRawType.isInstanceOf[Class[_]] => Some(extractGenericParams(t, t.getRawType.asInstanceOf[Class[_]]))
      case _ => None
    }
  }

  private def extractGenericParams(paramsType: ParameterizedType, paramsRawType: Class[_]): TypingResult = {
    Typed.genericTypeClass(paramsRawType, paramsType.getActualTypeArguments.toList.map(p => extractClass(p).getOrElse(Unknown)))
  }

  private def extractScalaVersionOfVarArgMethod(method: Method): Option[Method] = {
    val obj = method.getDeclaringClass
    val name = method.getName
    val args = method.getParameterTypes.toList
    args match {
      case noVarArgs :+ varArg if method.isVarArgs && varArg.isArray =>
        try {
          Some(obj.getMethod(name, noVarArgs :+ classOf[Seq[_]]: _*))
        } catch {
          case _: NoSuchMethodException => None
        }
      case _ => None
    }
  }

  private def extractJavaVersionOfVarArgMethod(method: Method): Option[Method] = {
    method.getDeclaringClass.getMethods.find(m => m.isVarArgs && (m.getParameterTypes.toList match {
      case noVarArgs :+ varArgArr if varArgArr.isArray =>
        method.getParameterTypes.toList == noVarArgs :+ classOf[Seq[_]]
      case _ => false
    }))
  }

  // "varargs" annotation creates new function that has java style varArgs
  // but it disregards annotations, so we have to look for original function
  // to extract them.
  private def extractAnnotation[T <: Annotation](obj: AnnotatedElement, annotationType: Class[T]): Option[T] =
    Option(obj.getAnnotation(annotationType)).orElse(obj match {
      case method: Method => extractScalaVersionOfVarArgMethod(method).flatMap(extractAnnotation(_, annotationType))
      // TODO: Add new case for parameters.
      case _ => None
    })

  def companionObject[T](klazz: Class[T]): T = {
    klazz.getField("MODULE$").get(null).asInstanceOf[T]
  }

}
