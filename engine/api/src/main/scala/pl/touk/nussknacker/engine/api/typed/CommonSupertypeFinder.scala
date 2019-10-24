package pl.touk.nussknacker.engine.api.typed

import java.lang

import pl.touk.nussknacker.engine.api.typed.typing._

/**
  * This class finding common supertype of two types. It basically based on fact that TypingResults are
  * sets of possible supertypes with some additional restrictions (like TypedObjectTypingResult).
  * It has very similar logic to CanBeSubclassDeterminer
  */
private[typed] object CommonSupertypeFinder {

  private val FloatingNumbers: Seq[Class[_]] = IndexedSeq(classOf[java.lang.Float], classOf[java.lang.Double],
    classOf[java.math.BigDecimal]).reverse

  private val DecimalNumbers: Seq[Class[_]] = IndexedSeq(
    classOf[java.lang.Byte], classOf[java.lang.Short], classOf[java.lang.Integer], classOf[java.lang.Long],
    classOf[java.math.BigInteger]).reverse

  private val Numbers = FloatingNumbers ++ DecimalNumbers

  private val SimpleTypes = Set(classOf[java.lang.Boolean], classOf[String]) ++ Numbers

  def findCommonSupertype(first: TypingResult, sec: TypingResult): TypingResult =
    (first, sec) match {
      case (Unknown, Unknown) => Unknown
      case (other, Unknown) => other
      case (Unknown, other) => other
      case (f: SingleTypingResult, s: TypedUnion) => Typed(findCommonSupertype(Set(f), s.possibleTypes))
      case (f: TypedUnion, s: SingleTypingResult) => Typed(findCommonSupertype(f.possibleTypes, Set(s)))
      case (f: SingleTypingResult, s: SingleTypingResult) => singleCommonSupertype(f, s)
      case (f: TypedUnion, s: TypedUnion) => Typed(findCommonSupertype(f.possibleTypes, s.possibleTypes))
    }

  private def findCommonSupertype(firstSet: Set[SingleTypingResult], secSet: Set[SingleTypingResult]): Set[TypingResult] =
    firstSet.flatMap(f => secSet.map(singleCommonSupertype(f, _)))


  private def singleCommonSupertype(first: SingleTypingResult, sec: SingleTypingResult): TypingResult =
    (first, sec) match {
      case (f: TypedObjectTypingResult, s: TypedObjectTypingResult) =>
        klassCommonSupertypeReturningTypedClass(f.objType, s.objType).map { commonSupertype =>
          val fields = for {
            firstField <- f.fields
            (name, firstFieldType) = firstField
            secFieldType <- s.fields.get(name)
            fieldCommonSuperType = findCommonSupertype(firstFieldType, secFieldType)
            if fieldCommonSuperType != Typed.empty
          } yield name -> fieldCommonSuperType
          if (fields.isEmpty)
            Typed.empty // no matching field - looks like we have totally different objects
          else
            TypedObjectTypingResult(fields, commonSupertype)
        }.getOrElse(Typed.empty)
      case (_: TypedObjectTypingResult, _) => Typed.empty
      case (_, _: TypedObjectTypingResult) => Typed.empty
      case (f: TypedClass, s: TypedClass) => klassCommonSupertype(f, s)
    }

  // This implementation is because TypedObjectTypingResult has underlying TypedClass instead of TypingResult
  private def klassCommonSupertypeReturningTypedClass(first: TypedClass, sec: TypedClass): Option[TypedClass] = {
    val boxedFirstClass = boxClass(first.klass)
    val boxedSecClass = boxClass(sec.klass)
    if (List(boxedFirstClass, boxedSecClass).forall(SimpleTypes.contains)) {
      commonSuperTypeForSimpleTypes(boxedFirstClass, boxedSecClass)
    } else {
      val forComplexTypes = commonSuperTypeForComplexTypes(boxedFirstClass, boxedSecClass)
      forComplexTypes match {
        case tc: TypedClass => Some(tc)
        case _ => None // empty, union and so on
      }
    }
  }

  private def klassCommonSupertype(first: TypedClass, sec: TypedClass): TypingResult = {
    val boxedFirstClass = boxClass(first.klass)
    val boxedSecClass = boxClass(sec.klass)
    if (List(boxedFirstClass, boxedSecClass).forall(SimpleTypes.contains)) {
      commonSuperTypeForSimpleTypes(boxedFirstClass, boxedSecClass).getOrElse(Typed.empty)
    } else {
      commonSuperTypeForComplexTypes(boxedFirstClass, boxedSecClass)
    }
  }

  private def boxClass(clazz: Class[_]) =
    clazz match {
      case p if p == classOf[Boolean] => classOf[lang.Boolean]
      case p if p == classOf[Byte] => classOf[lang.Byte]
      case p if p == classOf[Character] => classOf[Character]
      case p if p == classOf[Short] => classOf[lang.Short]
      case p if p == classOf[Int] => classOf[Integer]
      case p if p == classOf[Long] => classOf[lang.Long]
      case p if p == classOf[Float] => classOf[lang.Float]
      case p if p == classOf[Double] => classOf[lang.Double]
      case _ => clazz
    }

  private def commonSuperTypeForSimpleTypes(first: Class[_], sec: Class[_]): Option[TypedClass] = {
    if (Numbers.contains(first) && Numbers.contains(sec))
      Some(promotedNumberType(first, sec))
    else if (first == sec)
      Some(TypedClass(ClazzRef(first)))
    else
      None
  }

  private def promotedNumberType(first: Class[_], sec: Class[_]): TypedClass = {
    val both = List(first, sec)
    if (both.forall(FloatingNumbers.contains)) {
      TypedClass(ClazzRef(both.map(n => FloatingNumbers.indexOf(n) -> n).sortBy(_._1).map(_._2).head))
    } else if (both.forall(DecimalNumbers.contains)) {
      TypedClass(ClazzRef(both.map(n => DecimalNumbers.indexOf(n) -> n).sortBy(_._1).map(_._2).head))
    } else {
      assert(both.exists(DecimalNumbers.contains) && both.exists(FloatingNumbers.contains), s"Promoting unknown number type pair: $first, $sec") // shouldn't happen
      TypedClass(ClazzRef(both.find(FloatingNumbers.contains).get))
    }
  }

  private def commonSuperTypeForComplexTypes(first: Class[_], sec: Class[_]) = {
    if (first.isAssignableFrom(sec)) {
      Typed(first)
    } else if (sec.isAssignableFrom(first)) {
      Typed(sec)
    } else {
      // until here things are rather simple
      Typed(commonSuperTypeForTypesInNotSameInheritanceLine(first, sec).map(Typed(_)))
    }
  }

  private def commonSuperTypeForTypesInNotSameInheritanceLine(first: Class[_], sec: Class[_]): Set[Class[_]] = {
    // TODO: feature flag
    TypeHierarchyCommonSupertypeFinder.findCommonSupertypes(first, sec)
  }

}
