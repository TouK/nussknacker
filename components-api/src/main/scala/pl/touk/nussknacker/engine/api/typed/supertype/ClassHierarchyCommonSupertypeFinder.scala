package pl.touk.nussknacker.engine.api.typed.supertype

import scala.collection.compat._
import scala.collection.immutable.ListSet

/**
  * It looks for nearest common super type using algorithm described here: See https://stackoverflow.com/a/9797689
  * It means that for combination:
  * {{{
  * C <: B <: A
  * D <: B <: A
  * }}}
  * it will return only B (without A).
  * For combination:
  * {{{
  * C <: (B <: A) with (B' <: A')
  * D <: (B <: A) with (B' <: A')
  * }}}
  * it will return union (B or B')
  */
object ClassHierarchyCommonSupertypeFinder {

  private val IgnoredCommonInterfaces = Set[Class[_]](
    classOf[java.io.Serializable],
    classOf[java.lang.Comparable[_]],
    classOf[java.lang.Cloneable],
    classOf[scala.Serializable],
    classOf[scala.Cloneable],
    classOf[scala.Product],
  )

  def findCommonSupertypes(first: Class[_], sec: Class[_]): Set[Class[_]] = {
    // We need to have breadth first search to make reduction below work
    val firstBfs     = classesBfs(first)
    val secBfs       = classesBfs(sec)
    val intersection = firstBfs.intersect(secBfs)
    // We try to reduce this list - sometimes it is useful when it is exact one element (see klassCommonSupertypeReturningTypedClass)
    // also this type can be shown on FE
    intersection.foldLeft(Set.empty[Class[_]]) { (uniqueClasses, clazz) =>
      if (uniqueClasses.exists(clazz.isAssignableFrom)) {
        uniqueClasses
      } else {
        uniqueClasses + clazz
      }
    }
  }

  private def classesBfs(clazz: Class[_]): ListSet[Class[_]] = {
    bfsNodesForThisAndAllLevelsBelow(ListSet(clazz))
  }

  private def bfsNodesForThisAndAllLevelsBelow(classesOnThisLevel: ListSet[Class[_]]): ListSet[Class[_]] = {
    classesOnThisLevel ++ classesOnThisLevel.flatMap(classOnThisLevel =>
      bfsNodesForThisAndAllLevelsBelow(classesOnLowerLevel(classOnThisLevel))
    )
  }

  private def classesOnLowerLevel(classOnUpperLevel: Class[_]): ListSet[Class[_]] = {
    val superClassOpt = Option[Class[_]](classOnUpperLevel.getSuperclass)
    val interfaces    = ListSet.from(classOnUpperLevel.getInterfaces)
    ListSet.from(superClassOpt.filterNot(_ == classOf[Object]).toList) ++ interfaces.diff(IgnoredCommonInterfaces)
  }

}
