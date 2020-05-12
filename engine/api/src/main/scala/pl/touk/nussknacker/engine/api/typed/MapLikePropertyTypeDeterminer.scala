package pl.touk.nussknacker.engine.api.typed

import java.util

import pl.touk.nussknacker.engine.api.typed.typing.{Typed, TypedClass, TypingResult, Unknown}

object MapLikePropertyTypeDeterminer {

  def mapLikePropertyType(typ: TypedClass): Option[TypingResult] = typ match {
    case TypedClass(cl, _ :: valueParam :: Nil) if classOf[util.Map[_, _]].isAssignableFrom(cl) => Some(valueParam)
    case TypedClass(cl, _) if classOf[util.Map[_, _]].isAssignableFrom(cl) => Some(Unknown)
    // For avro specific records (generated) we want to verify exact fields
    case TypedClass(cl, _) if isAvroSpecificRecord(cl) => None
    case TypedClass(cl, _) => getMethodReturnType(cl)
  }

  private def isAvroSpecificRecord(cl: Class[_]) = {
    // because we don't want to strict coupling with avro in this odule
    cl.getInterfaces.exists(_.getName == "org.apache.avro.specific.SpecificRecord")
  }

  // see MapLikePropertyAccessor
  private def getMethodReturnType(cl: Class[_]) =
    cl.getMethods
      .find(m => m.getName == "get" && (m.getParameterTypes sameElements Array(classOf[String])))
      .map(m => Typed(m.getReturnType))

}
