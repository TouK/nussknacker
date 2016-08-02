package pl.touk.esp.engine.api

import java.lang.reflect.Method

trait Context {

  def processMetaData: MetaData

  def variables: Map[String, Any]

  def expressionFunctions: Map[String, Method]

  def apply[T](name: String): T =
    getOrElse(name, throw new RuntimeException(s"Unknown variable: $name"))

  def getOrElse[T](name: String, default: => T) =
    get(name).getOrElse(default)

  def get[T](name: String): Option[T] =
    variables.get(name).map(_.asInstanceOf[T])

}
