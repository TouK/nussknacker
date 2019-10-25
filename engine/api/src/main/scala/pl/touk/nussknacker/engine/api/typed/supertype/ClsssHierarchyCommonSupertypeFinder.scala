package pl.touk.nussknacker.engine.api.typed.supertype

import scala.collection.mutable

/**
  * It looks for common super type using algorithm described here: See https://stackoverflow.com/a/9797689
  */
object ClsssHierarchyCommonSupertypeFinder {

  private val IgnoredCommonInterfaces = Set[Class[_]](
    classOf[Serializable],
    classOf[Comparable[_]],
    classOf[Cloneable],
    classOf[Product]
  )

  def findCommonSupertypes(first: Class[_], sec: Class[_]): Set[Class[_]] = {
    val firstBfs = classesBfs(first)
    val secBfs = classesBfs(sec)
    val intersection = firstBfs.intersect(secBfs)
    // We try to reduce this list - sometimes it is useful when it is exact one element (see klassCommonSupertypeReturningTypedClass)
    intersection.foldLeft(Set.empty[Class[_]]) { (uniqueClasses, clazz) =>
      if (uniqueClasses.exists(clazz.isAssignableFrom)) {
        uniqueClasses
      } else {
        uniqueClasses + clazz
      }
    }
  }

  // We are using mutable.LinkedHashSet instead of immutable.ListSet because of this bug in Scala 2.11: https://github.com/scala/bug/issues/10005
  // For ++, flatMap and intersect it returns copy of set
  private def classesBfs(clazz: Class[_]): mutable.LinkedHashSet[Class[_]] = {
    bfsNodesForThisAndAllLevelsBelow(mutable.LinkedHashSet(clazz))
  }

  private def bfsNodesForThisAndAllLevelsBelow(classesOnThisLevel: mutable.LinkedHashSet[Class[_]]): mutable.LinkedHashSet[Class[_]] = {
    classesOnThisLevel ++ classesOnThisLevel.flatMap(classOnThisLevel => bfsNodesForThisAndAllLevelsBelow(classesOnLowerLevel(classOnThisLevel)))
  }

  private def classesOnLowerLevel(classOnUpperLevel: Class[_]): mutable.LinkedHashSet[Class[_]] = {
    mutable.LinkedHashSet(Option[Class[_]](
      classOnUpperLevel.getSuperclass).filterNot(_ == classOf[Object]).toArray: _*) ++
      mutable.LinkedHashSet[Class[_]](classOnUpperLevel.getInterfaces.filterNot(IgnoredCommonInterfaces.contains): _*)
  }

}
