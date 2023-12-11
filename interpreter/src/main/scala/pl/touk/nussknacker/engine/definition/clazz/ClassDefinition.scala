package pl.touk.nussknacker.engine.definition.clazz

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}

case class ClassDefinition(
    clazzName: TypingResult,
    methods: Map[String, List[MethodDefinition]],
    staticMethods: Map[String, List[MethodDefinition]]
) {

  def getClazz: Class[_] = clazzName match {
    case TypedClass(klass, _) => klass
    case Unknown              => AnyClass
    case typingResult =>
      throw new IllegalAccessException(
        s"$typingResult not supported. Class and Unknown are only valid inputs for fragment."
      )
  }

  private def asProperty(info: MethodDefinition): Option[TypingResult] = info.computeResultType(List()).toOption

  private val AnyClass: Class[Any] = classOf[Any]

  def getPropertyOrFieldType(methodName: String): Option[TypingResult] = {
    def filterMethods(candidates: Map[String, List[MethodDefinition]]): List[TypingResult] =
      candidates.get(methodName).toList.flatMap(_.map(asProperty)).collect { case Some(x) => x }
    val filteredMethods       = filterMethods(methods)
    val filteredStaticMethods = filterMethods(staticMethods)
    val filtered              = filteredMethods ++ filteredStaticMethods
    filtered match {
      case Nil      => None
      case nonEmpty => Some(Typed(nonEmpty.toSet))
    }
  }

}
