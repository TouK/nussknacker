package pl.touk.nussknacker.engine.definition.component.parameter.defaults

object TypeValueDeterminer {

  private val numbersClasses: Set[AnyRef] = Set(
    classOf[Int],
    classOf[Short],
    classOf[Long],
    classOf[java.lang.Number],
    classOf[java.lang.Long],
    classOf[java.lang.Short],
    classOf[java.lang.Integer],
    classOf[java.math.BigInteger],
  )

  private val floatingPointNumbersClasses: Set[AnyRef] = Set(
    classOf[Float],
    classOf[Double],
    classOf[java.math.BigDecimal],
    classOf[java.lang.Float],
    classOf[java.lang.Double],
  )

  private val stringClass = classOf[String]

  private val booleanClasses: Set[AnyRef] = Set(
    classOf[java.lang.Boolean],
    classOf[Boolean],
  )

  private val listClass = classOf[java.util.List[_]]
  private val mapClass  = classOf[java.util.Map[_, _]]

  def isIntegerNumber(clazz: Class[_]): Boolean       = numbersClasses.contains(clazz)
  def isFloatingPointNumber(clazz: Class[_]): Boolean = floatingPointNumbersClasses.contains(clazz)

  def isBoolean(clazz: Class[_]): Boolean = booleanClasses.contains(clazz)

  def isString(clazz: Class[_]): Boolean = stringClass.equals(clazz)
  def isList(clazz: Class[_]): Boolean   = listClass.equals(clazz)
  def isMap(clazz: Class[_]): Boolean    = mapClass.equals(clazz)
}
