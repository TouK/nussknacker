package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.exception.ExcludedPatternInvocationException

import scala.util.matching.Regex

object SpelExpressionExcludeList {

  val default: SpelExpressionExcludeList = SpelExpressionExcludeList(
    List(
      "(org\\.springframework)".r,
      "(java\\.lang\\.System)".r,
      "(java\\.lang\\.Thread)".r,
      "(java\\.lang\\.Runtime)".r,
      "(java\\.lang\\.ProcessBuilder)".r,
      "(java\\.lang\\.invoke)".r,
      "(java\\.lang\\.reflect)".r,
      "(java\\.net)".r,
      "(java\\.io)".r,
      "(java\\.nio)".r,
      "(exec\\()".r,
    ))
}

case class SpelExpressionExcludeList(excludedPatterns: List[Regex]) {

  def blockExcluded(targetObject: Object, methodName: String): Unit = {
    val classFullName = getClassNameFromObject(targetObject)
    excludedPatterns.find(excluded =>
      excluded.findFirstMatchIn(classFullName) match {
        case Some(_) => throw ExcludedPatternInvocationException(s"Method ${methodName} of class ${classFullName} is not allowed to be passed as a spel expression")
        case None => false
      })
  }

  private def getClassNameFromObject(targetObject: Object): String = {
    targetObject match {
      case targetClass: Class[_] => targetClass.getName
      case _: Object => targetObject.getClass.getName
    }
  }
}

