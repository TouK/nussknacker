package pl.touk.nussknacker.engine.definition.component.parameter.defaults

object TypeValueDeterminer {

  private val likeIntegerNumbersClassNames = Set(
    "long",
    "short",
    "int",
    "java.lang.Number",
    "java.lang.Long",
    "java.lang.Short",
    "java.lang.Integer",
    "java.math.BigInteger",
  )

  private val likeFloatingPointNumbersClassNames = Set(
    "float",
    "double",
    "java.math.BigDecimal",
    "java.lang.Float",
    "java.lang.Double"
  )

  private val stringClass = "java.lang.String"
  private val listClass   = "java.util.List"
  private val mapClass    = "java.util.Map"

  def isLikeIntegerNumber(className: String): Boolean       = likeIntegerNumbersClassNames.contains(className)
  def isLikeFloatingPointNumber(className: String): Boolean = likeFloatingPointNumbersClassNames.contains(className)

  def isBoolean(className: String): Boolean = className match {
    case "boolean" | "java.lang.Boolean" => true
    case _                               => false
  }

  def isString(className: String): Boolean = stringClass.equals(className)
  def isList(className: String): Boolean   = listClass.equals(className)
  def isMap(className: String): Boolean    = mapClass.equals(className)
}
