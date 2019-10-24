package pl.touk.nussknacker.engine.api.typed

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
        klassCommonSupertype(f.objType, s.objType).map { commonSupertype =>
          val fields = for {
            firstField <- f.fields
            (name, firstFieldType) = firstField
            secFieldType <- s.fields.get(name)
            fieldCommonSuperType = findCommonSupertype(firstFieldType, secFieldType)
            if fieldCommonSuperType != Typed.empty
          } yield name -> fieldCommonSuperType
          TypedObjectTypingResult(fields, commonSupertype)
        }.getOrElse(Typed.empty)
      case (_: TypedObjectTypingResult, _) => Typed.empty
      case (_, _: TypedObjectTypingResult) => Typed.empty
      case (f: TypedClass, s: TypedClass) => klassCommonSupertype(f, s).getOrElse(Typed.empty)
    }

  private def klassCommonSupertype(first: TypedClass, sec: TypedClass): Option[TypedClass] = {
    inheritancePath(first).zip(inheritancePath(sec)).map {
      case (f, s) if f == s => Some(f)
      case (f, s) if List(f, s).forall(FloatingNumbers.contains) =>
        List(f, s).map(n => FloatingNumbers.indexOf(n) -> n).sortBy(_._1).map(_._2).headOption
      case (f, s) if List(f, s).forall(DecimalNumbers.contains) =>
        List(f, s).map(n => DecimalNumbers.indexOf(n) -> n).sortBy(_._1).map(_._2).headOption
      case (f, s) if List(f, s).exists(DecimalNumbers.contains) && List(f, s).exists(FloatingNumbers.contains)  =>
        List(f, s).find(FloatingNumbers.contains)
      case _ => None
    }.takeWhile(_.isDefined).lastOption.map(t => TypedClass(ClazzRef(t.get)))
  }

  private def inheritancePath(clazz: TypedClass) = {
    // for simple types (like int), getSuperclass returns null
    val boxedClass = clazz.klass match {
      case p if p == classOf[Boolean]  => classOf[java.lang.Boolean]
      case p if p == classOf[Byte]  => classOf[java.lang.Byte]
      case p if p == classOf[Character]  => classOf[java.lang.Character]
      case p if p == classOf[Short]  => classOf[java.lang.Short]
      case p if p == classOf[Int]  => classOf[java.lang.Integer]
      case p if p == classOf[Long]  => classOf[java.lang.Long]
      case p if p == classOf[Float]  => classOf[java.lang.Float]
      case p if p == classOf[Double]  => classOf[java.lang.Double]
      case _ => clazz.klass
    }
    Stream.iterate[Option[Class[_]]](Some(boxedClass))(_.flatMap(cl => Option[Class[_]](cl.getSuperclass)))
      .takeWhile(_.exists(_ != classOf[Object])).map(_.get).toList.reverse
  }

}
