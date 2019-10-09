package pl.touk.nussknacker.engine.spel.ast

import org.springframework.expression.TypedValue
import org.springframework.expression.spel.SpelNode
import org.springframework.expression.spel.ast._

import scala.reflect._

/*
  This class exists because SpelNode implementations have private access to some important fields
 */
object SpelNodeHacks {

  def getPosition(e: SpelNodeImpl): Int =
    getField[Int](e, classOf[SpelNodeImpl], "pos")

  def getFunctionName(e: FunctionReference): String =
    getField[String](e, "name")

  def getQualifiedId(e: QualifiedIdentifier): TypedValue =
    getField[TypedValue](e, "value")

  def getVariant(e: Selection): Int =
    getField[Int](e, "variant")

  def getDimensions(e: TypeReference): Int =
    getField[Int](e, "dimensions")

  def isPostfix(e: Operator): Boolean =
    getField[Boolean](e, "postfix")

  def isNullSafe(e: MethodReference): Boolean = {
    getField[Boolean](e, "nullSafe")
  }

  def isNullSafe(e: Projection): Boolean = {
    getField[Boolean](e, "nullSafe")
  }

  def isNullSafe(e: Selection): Boolean = {
    getField[Boolean](e, "nullSafe")
  }

  private def getField[T: ClassTag](e: SpelNode, name: String): T =
    getField[T](e, e.getClass, name)

  private def getField[T: ClassTag](e: SpelNode, clazz: Class[_], name: String): T = {
    val field = clazz.getDeclaredField(name)
    field.setAccessible(true)
    val runtimeClass = classTag[T].runtimeClass
    val value =
      if (runtimeClass == classOf[Boolean]) {
        field.getBoolean(e)
      } else if (runtimeClass == classOf[Int]) {
        field.getInt(e)
      } else {
        field.get(e)
      }
    value.asInstanceOf[T]
  }

}
