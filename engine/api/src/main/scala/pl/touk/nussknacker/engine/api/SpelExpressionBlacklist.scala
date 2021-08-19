package pl.touk.nussknacker.engine.api

import pl.touk.nussknacker.engine.api.exception.BlacklistedPatternInvocationException

import scala.util.matching.Regex

object SpelExpressionBlacklist {

  val default: SpelExpressionBlacklist = SpelExpressionBlacklist(
    Map(
      "spring" -> "(org\\.springframework)".r,
      "system" -> "(java\\.lang\\.System)".r,
      "thread" -> "(java\\.lang\\.Thread)".r,
      "runtime" -> "(java\\.lang\\.Runtime)".r,
      "invoke" -> "(java\\.lang\\.invoke)".r,
      "reflect" -> "(java\\.lang\\.reflect)".r,
      "net" -> "(java\\.net)".r,
      "io" -> "(java\\.io)".r,
      "nio" -> "(java\\.nio)".r,
      "exec" -> "(exec\\()".r
    ))
}

case class SpelExpressionBlacklist(blacklistedPatterns: Map[String, Regex]) {

  private def getClassNameFromObject(targetObject: Object): String = {
    targetObject match {
      case targetClass: Class[_] => targetClass.getName
      case _: Object => targetObject.getClass.getName
    }
  }

  def blockBlacklisted(targetObject: Object, methodName: String): Unit = {
    val classFullName = getClassNameFromObject(targetObject)
    blacklistedPatterns.find(blackListed =>
      blackListed._2.findFirstMatchIn(classFullName) match {
        case Some(_) => throw BlacklistedPatternInvocationException(s"Method ${methodName} of class ${classFullName} is not allowed to be passed as a spel expression")
        case None => false
      })
  }
}

