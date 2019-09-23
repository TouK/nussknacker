package pl.touk.nussknacker.engine.api.typed

import org.scalatest.{FunSpec, Inside, Matchers}

class ClazzRefSpec extends FunSpec with Matchers with Inside {

  it ("should deeply extract typ parameters") {
    inside(ClazzRef.detailed[Option[Map[String, Int]]]) {
      case ClazzRef(_, optionClass, mapTypeArg :: Nil) if optionClass == classOf[Option[Any]] =>
        inside(mapTypeArg) {
          case ClazzRef(_, optionClass, keyTypeArg :: valueTypeArg :: Nil) if optionClass == classOf[Map[Any, Any]] =>
            inside(keyTypeArg) {
              case ClazzRef(_, keyClass, Nil) =>
                keyClass shouldBe classOf[String]
            }
            inside(valueTypeArg) {
              case ClazzRef(_, keyClass, Nil) =>
                keyClass shouldBe classOf[Int]
            }
        }
    }
  }

}
