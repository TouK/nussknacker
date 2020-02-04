package pl.touk.nussknacker.engine.api.typed

import org.scalatest.{FunSpec, Inside, Matchers}
import pl.touk.nussknacker.engine.api.typed.typing.TypedClass

class ClazzRefSpec extends FunSpec with Matchers with Inside {

  it ("should deeply extract typ parameters") {
    inside(TypedClass.fromDetailedType[Option[Map[String, Int]]]) {
      case TypedClass(optionClass, mapTypeArg :: Nil) if optionClass == classOf[Option[Any]] =>
        inside(mapTypeArg) {
          case TypedClass(optionClass, keyTypeArg :: valueTypeArg :: Nil) if optionClass == classOf[Map[Any, Any]] =>
            inside(keyTypeArg) {
              case TypedClass(keyClass, Nil) =>
                keyClass shouldBe classOf[String]
            }
            inside(valueTypeArg) {
              case TypedClass(keyClass, Nil) =>
                keyClass shouldBe classOf[Int]
            }
        }
    }
  }

}
