package pl.touk.nussknacker.engine.definition.clazz

import cats.data.{NonEmptyList, StateT}
import cats.data.Validated.{Invalid, Valid}
import cats.effect.IO
import cats.implicits.catsSyntaxSemigroup
import com.typesafe.scalalogging.LazyLogging
import org.apache.commons.lang3.{ClassUtils, StringUtils}
import pl.touk.nussknacker.engine.api.{Documentation, ParamName}
import pl.touk.nussknacker.engine.api.generics.{GenericType, MethodTypeInfo, Parameter, TypingFunction}
import pl.touk.nussknacker.engine.api.process.{
  ClassExtractionSettings,
  TypingFunctionForClassMember,
  VisibleMembersPredicate
}
import pl.touk.nussknacker.engine.api.process.PropertyFromGetterExtractionStrategy.{
  AddPropertyNextToGetter,
  DoNothing,
  ReplaceGetterWithProperty
}
import pl.touk.nussknacker.engine.api.typed.typing._
import pl.touk.nussknacker.engine.definition.clazz.ClassDefinitionExtractor.{
  extractClass,
  extractGenericReturnType,
  extractMethodReturnType,
  extractParameterType
}

import java.lang.annotation.Annotation
import java.lang.reflect._
import java.util.Optional

class ClassDefinitionExtractor(settings: ClassExtractionSettings) extends LazyLogging {

  import pl.touk.nussknacker.engine.util.Implicits._

  def extract(clazz: Class[_]): ClassDefinition =
    ClassDefinition(
      Typed(clazz),
      extractPublicMethodsAndFields(clazz, staticMethodsAndFields = false),
      extractPublicMethodsAndFields(clazz, staticMethodsAndFields = true)
    )

  def isHidden(clazz: Class[_]): Boolean =
    settings.isHidden(clazz)

  private def extractPublicMethodsAndFields(
      clazz: Class[_],
      staticMethodsAndFields: Boolean
  ): Map[String, List[MethodDefinition]] = {
    val membersPredicate = settings.visibleMembersPredicate(clazz)
    val methods          = extractPublicMethods(clazz, membersPredicate, staticMethodsAndFields)
    val fields           = extractPublicFields(clazz, membersPredicate, staticMethodsAndFields).mapValuesNow(List(_))
    filterHiddenParameterAndReturnType(methods ++ fields)
  }

  private def extractPublicMethods(
      clazz: Class[_],
      membersPredicate: VisibleMembersPredicate,
      staticMethodsAndFields: Boolean
  ): Map[String, List[MethodDefinition]] = {
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
      if (staticMethodsAndFields)
        publicMethods.filter(membersPredicate.shouldBeVisible).filter(m => Modifier.isStatic(m.getModifiers))
      else publicMethods.filter(membersPredicate.shouldBeVisible).filter(m => !Modifier.isStatic(m.getModifiers))

    // "varargs" annotation generates two methods - one with scala style varArgs
    // and one with java style varargs. We want only the second one so we have
    // to filter them.
    val filteredMethods = methods.filter(extractJavaVersionOfVarArgMethod(_).isEmpty)

    val methodNameAndInfoList = filteredMethods
      .flatMap(extractMethod(clazz, _))

    val staticMethodDefinitions =
      methodNameAndInfoList
        .filter(_._2.isInstanceOf[StaticMethodDefinition])
        .asInstanceOf[List[(String, StaticMethodDefinition)]]
    val functionalMethodDefinitions        = methodNameAndInfoList.filter(_._2.isInstanceOf[FunctionalMethodDefinition])
    val groupedFunctionalMethodDefinitions = functionalMethodDefinitions.groupBy(_._1).mapValuesNow(_.map(_._2))

    deduplicateMethodsWithGenericReturnType(staticMethodDefinitions)
      .asInstanceOf[Map[String, List[MethodDefinition]]]
      .combine(groupedFunctionalMethodDefinitions)
  }

  // We have to filter here, not in ClassExtractionSettings, as we do e.g. boxed/unboxed mapping on TypedClass level...
  private def filterHiddenParameterAndReturnType(
      infos: Map[String, List[MethodDefinition]]
  ): Map[String, List[MethodDefinition]] = {
    def typeResultVisible(t: TypingResult): Boolean = t match {
      case str: SingleTypingResult =>
        !settings.isHidden(str.runtimeObjType.klass) && str.runtimeObjType.params.forall(typeResultVisible)
      case union: TypedUnion => union.possibleTypes.forall(typeResultVisible)
      case TypedNull         => true
      case Unknown           => true
    }
    def filterOneMethod(method: MethodDefinition): Boolean = {
      val noVarArgTypes = method.signatures.toList.flatMap(_.noVarArgs).map(_.refClazz)
      val varArgTypes   = method.signatures.toList.flatMap(_.varArg.toList).map(_.refClazz)
      val resultTypes   = method.signatures.toList.map(_.result)
      (noVarArgTypes ::: varArgTypes ::: resultTypes).forall(typeResultVisible)
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
  private def deduplicateMethodsWithGenericReturnType(methodNameAndInfoList: List[(String, StaticMethodDefinition)]) = {
    val groupedByNameAndParameters =
      methodNameAndInfoList.groupBy(mi => (mi._1, mi._2.signature.noVarArgs, mi._2.signature.varArg))
    groupedByNameAndParameters.toList
      .map { case (_, methodsForParams) =>
        /*
          we want to find "most specific" class, however surprisingly it's not always possible, because we treat e.g. isLeft and left methods
          as equal (for javabean-like access) and e.g. in scala Either this is perfectly possible. In case we cannot find most specific
          class we pick arbitrary one (we sort to avoid randomness)
         */

        methodsForParams
          .find { case (_, method) =>
            methodsForParams.forall(mi => method.signature.result.canBeConvertedTo(mi._2.signature.result))
          }
          .getOrElse(methodsForParams.minBy(_._2.signature.result.display))
      }
      .toGroupedMap
      // we sort only to avoid randomness
      .mapValuesNow(_.sortBy(_.toString))
  }

  // SpEL is able to access getters using property name so you can write `obj.foo` instead of `obj.getFoo`
  private def collectMethodNames(method: Method): List[String] = {
    val isGetter = method.getName.matches("^(get|is).+") && method.getParameterCount == 0
    if (isGetter) {
      val propertyMethod = StringUtils.uncapitalize(method.getName.replaceAll("^get|^is", ""))
      settings.propertyExtractionStrategy match {
        case AddPropertyNextToGetter   => List(method.getName, propertyMethod)
        case ReplaceGetterWithProperty => List(propertyMethod)
        case DoNothing                 => List(method.getName)
      }
    } else {
      List(method.getName)
    }
  }

  private def extractMethod(
      clazz: Class[_],
      method: Method
  ): List[(String, MethodDefinition)] =
    extractAnnotation(method, classOf[GenericType]) match {
      case None             => extractRegularMethod(clazz, method)
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
        throw new IllegalArgumentException(
          s"Could not find parameterless constructor for method ${method.getName} or its TypingFunction was declared inside non-static class.",
          e
        )
      case e: Exception =>
        throw new IllegalArgumentException(s"Could not extract information about generic method ${method.getName}.", e)
    }
  }

  private def extractGenericMethod(method: Method, genericType: GenericType): List[(String, MethodDefinition)] = {
    val typeFunctionInstance = getTypeFunctionInstanceFromAnnotation(method, genericType)

    val methodTypeInfo = extractGenericParameters(typeFunctionInstance, method)

    collectMethodNames(method).map(methodName =>
      methodName -> FunctionalMethodDefinition(
        (_, x) => typeFunctionInstance.computeResultType(x),
        methodTypeInfo,
        methodName,
        extractNussknackerDocs(method)
      )
    )
  }

  private def extractRegularMethod(
      clazz: Class[_],
      method: Method
  ): List[(String, MethodDefinition)] = {
    collectMethodNames(method).map { methodName =>
      val reflectionBasedDefinition = extractMethodTypeInfo(method)
      val methodDefinition = extractMemberMethodDefinition(clazz, method, methodName, reflectionBasedDefinition)
      methodName -> methodDefinition
    }
  }

  private def extractPublicFields(
      clazz: Class[_],
      membersPredicate: VisibleMembersPredicate,
      staticMethodsAndFields: Boolean
  ): Map[String, MethodDefinition] = {
    val interestingFields = clazz.getFields.filter(membersPredicate.shouldBeVisible)
    val fields =
      if (staticMethodsAndFields) interestingFields.filter(m => Modifier.isStatic(m.getModifiers))
      else interestingFields.filter(m => !Modifier.isStatic(m.getModifiers))
    fields.map { field =>
      val reflectionBasedDefinition = MethodTypeInfo(Nil, None, extractFieldReturnType(field))
      val methodDefinition = extractMemberMethodDefinition(clazz, field, field.getName, reflectionBasedDefinition)
      field.getName -> methodDefinition
    }.toMap
  }

  private def extractMemberMethodDefinition(
      clazz: Class[_],
      member: Member with AccessibleObject,
      memberName: String,
      reflectionBasedDefinition: MethodTypeInfo
  ): MethodDefinition = {
    settings
      .typingFunction(clazz, member)
      .map(prepareFunctionMethodDefinition(clazz, member, memberName, reflectionBasedDefinition, _))
      .getOrElse {
        StaticMethodDefinition(
          reflectionBasedDefinition,
          memberName,
          extractNussknackerDocs(member)
        )
      }
  }

  private def prepareFunctionMethodDefinition(
      clazz: Class[_],
      member: Member with AccessibleObject,
      memberName: String,
      reflectionBasedDefinition: MethodTypeInfo,
      typingFun: TypingFunctionForClassMember
  ) = {
    FunctionalMethodDefinition(
      (invocationTarget, _) => {
        Valid(invocationTarget.withoutValue match {
          case single: SingleTypingResult =>
            val returnedResultType = typingFun.resultType(single).valueOr { message =>
              logger.warn(
                s"ClassExtractionSettings defined typing function for class: ${clazz.getName}, member: ${member.getName} " +
                  s"which returned error during result type computation: $message. " +
                  s"Will be used fallback result type: ${reflectionBasedDefinition.result}"
              )
              reflectionBasedDefinition.result
            }
            if (returnedResultType.canBeConvertedTo(returnedResultType)) {
              returnedResultType
            } else {
              logger.warn(
                s"ClassExtractionSettings defined typing function for class: ${clazz.getName}, member: ${member.getName} " +
                  s"which returned result type $returnedResultType that can't be a subclass of type ${reflectionBasedDefinition.result} " +
                  s"computed using reflection. Will be used type computed using reflection"
              )
              reflectionBasedDefinition.result
            }
          case _ => reflectionBasedDefinition.result
        })
      },
      reflectionBasedDefinition,
      memberName,
      extractNussknackerDocs(member)
    )
  }

  private def extractNussknackerDocs(accessibleObject: AccessibleObject): Option[String] = {
    extractAnnotation(accessibleObject, classOf[Documentation]).map(_.description())
  }

  private def extractGenericParameters(typingFunction: TypingFunction, method: Method): NonEmptyList[MethodTypeInfo] = {
    val autoExtractedParameters = extractMethodTypeInfo(method)
    val definedParametersOption = typingFunction.signatures.toList.flatMap(_.toList)

    definedParametersOption
      .map(MethodTypeInfoSubclassChecker.check(_, autoExtractedParameters))
      .collect { case Invalid(e) => e }
      .foreach { x =>
        val errorString = x.map(_.message).toList.mkString("; ")
        throw new IllegalArgumentException(
          s"Generic function ${method.getName} has declared parameters that are incompatible with methods signature: $errorString"
        )
      }

    NonEmptyList.fromList(definedParametersOption).getOrElse(NonEmptyList.one(autoExtractedParameters))
  }

  private def extractMethodTypeInfo(method: Method): MethodTypeInfo = {
    MethodTypeInfo.fromList(
      for {
        param <- method.getParameters.toList
        annotationOption = extractAnnotation(param, classOf[ParamName])
        name             = annotationOption.map(_.value).getOrElse(param.getName)
        paramType        = extractParameterType(param)
      } yield Parameter(name, paramType),
      method.isVarArgs,
      extractMethodReturnType(method)
    )
  }

  private def extractFieldReturnType(field: Field): TypingResult = {
    extractGenericReturnType(field.getGenericType)
      .orElse(extractClass(field.getGenericType))
      .getOrElse(Typed(field.getType))
  }

  private def extractScalaVersionOfVarArgMethod(method: Method): Option[Method] = {
    val obj  = method.getDeclaringClass
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
    method.getDeclaringClass.getMethods.find(m =>
      m.isVarArgs && (m.getParameterTypes.toList match {
        case noVarArgs :+ varArgArr if varArgArr.isArray =>
          method.getParameterTypes.toList == noVarArgs :+ classOf[Seq[_]]
        case _ => false
      })
    )
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

}

object ClassDefinitionExtractor {

  def extractMethodReturnType(method: Method): TypingResult = {
    extractGenericReturnType(method.getGenericReturnType)
      .orElse(extractClass(method.getGenericReturnType))
      .getOrElse(Typed(method.getReturnType))
  }

  def extractParameterType(javaParam: java.lang.reflect.Parameter): TypingResult = {
    extractClass(javaParam.getParameterizedType).getOrElse(Typed(javaParam.getType))
  }

  def companionObject[T](klazz: Class[T]): T = {
    klazz.getField("MODULE$").get(null).asInstanceOf[T]
  }

  private def extractGenericReturnType(typ: Type): Option[TypingResult] = {
    typ match {
      case t: ParameterizedType if t.getRawType.isInstanceOf[Class[_]] =>
        extractGenericMonadReturnType(t, t.getRawType.asInstanceOf[Class[_]])
      case _ => None
    }
  }

  // This method should be used only for method's and field's return type - for method's parameters such unwrapping has no sense
  //
  // Arguments of generic types that are Scala's primitive types are always erased by Scala compiler to java.lang.Object:
  // * issue: https://github.com/scala/bug/issues/4214 (and discussion at https://groups.google.com/g/scala-internals/c/K2dELqajQbg/m/gV0tbjRHJ4UJ)
  // * commit: https://github.com/scala/scala/commit/e42733e9fe1f3af591976fbb48b66035253d85b9
  private def extractGenericMonadReturnType(
      genericReturnType: ParameterizedType,
      genericReturnRawType: Class[_]
  ): Option[TypingResult] = {
    // see ScalaLazyPropertyAccessor
    if (classOf[StateT[IO, _, _]].isAssignableFrom(genericReturnRawType)) {
      val returnType = genericReturnType.getActualTypeArguments.apply(
        3
      ) // it's IndexedStateT[IO, ContextWithLazyValuesProvider, ContextWithLazyValuesProvider, A]
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
    } else None
  }

  // TODO this is not correct for primitives and complicated hierarchies, but should work in most cases
  // http://docs.oracle.com/javase/8/docs/api/java/lang/reflect/ParameterizedType.html#getActualTypeArguments--
  private def extractClass(typ: Type): Option[TypingResult] = {
    typ match {
      case t: Class[_] => Some(Typed(t))
      case t: ParameterizedType if t.getRawType.isInstanceOf[Class[_]] =>
        Some(extractGenericParams(t, t.getRawType.asInstanceOf[Class[_]]))
      case _ => None
    }
  }

  private def extractGenericParams(paramsType: ParameterizedType, paramsRawType: Class[_]): TypingResult = {
    Typed.genericTypeClass(
      paramsRawType,
      paramsType.getActualTypeArguments.toList.map(p => extractClass(p).getOrElse(Unknown))
    )
  }

}
