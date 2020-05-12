package pl.touk.nussknacker.engine.spel.typer

import java.util

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}

/**
 * This class determine type of values for classes which behave like a maps - basically implementing Maps or having get(key) method.
 * We want to be able to know type of their field
 */
object MapLikePropertyTyper {

  // exclusion list - we have strings here because they are in optional dependencies
  private val ExcludedMapLikeInterfaces = Set(
    // For avro specific records (generated) we want to verify exact fields
    "org.apache.avro.specific.SpecificRecord")

  /**
   * @param typ typed class
   * @return Some(valueType) if `typ` is map-like class, None otherwise
   */
  def mapLikeValueType(typ: TypedClass): Option[TypingResult] = typ match {
    case TypedClass(cl, _) if isExcludedMapLikeClass(cl) => None
    // see MapPropertyAccessor
    case TypedClass(cl, _ :: valueParam :: Nil) if classOf[util.Map[_, _]].isAssignableFrom(cl) => Some(valueParam)
    case TypedClass(cl, _) if classOf[util.Map[_, _]].isAssignableFrom(cl) => Some(Unknown)
    case TypedClass(cl, _) => getMethodReturnType(cl)
  }

  private def isExcludedMapLikeClass(cl: Class[_]) =
    cl.getInterfaces.exists(i => ExcludedMapLikeInterfaces.contains(i.getName))

  // see MapLikePropertyAccessor
  private def getMethodReturnType(cl: Class[_]) =
    cl.getMethods
      .find(m => m.getName == "get" && (m.getParameterTypes sameElements Array(classOf[String])))
      .map(m => Typed(m.getReturnType))

}
