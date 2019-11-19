package pl.touk.nussknacker.engine.util.loader

import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor3}
import org.scalatest.{FlatSpec, Matchers}

class ScalaServiceLoaderSpec extends FlatSpec with Matchers with TableDrivenPropertyChecks {

  behavior of "ScalaServiceLoader.chooseClass"

  trait DummyFactoryTrait
  case object DummyAuthenticatorFactory extends DummyFactoryTrait
  case object DummyAuthenticatorFactory2 extends DummyFactoryTrait
  case object DummyAuthenticatorFactory3 extends DummyFactoryTrait

  it should "Load class from implementations" in {
    val table: TableFor3[List[DummyFactoryTrait], DummyFactoryTrait, DummyFactoryTrait] = Table(
      ("class factories", "default factory", "chosen factory"),
      (DummyAuthenticatorFactory :: Nil, DummyAuthenticatorFactory, DummyAuthenticatorFactory),
      (List(DummyAuthenticatorFactory3), DummyAuthenticatorFactory, DummyAuthenticatorFactory3),
      (List(DummyAuthenticatorFactory3, DummyAuthenticatorFactory2), DummyAuthenticatorFactory, DummyAuthenticatorFactory3)
    )

    forAll(table) {
      (factories: List[DummyFactoryTrait], default: DummyFactoryTrait, chosen: DummyFactoryTrait) => {
        try {
          ScalaServiceLoader.chooseClass[DummyFactoryTrait]({default}, factories) match {
            case loaded: DummyFactoryTrait => chosen shouldBe loaded
          }
        } catch {
          case _ : IllegalArgumentException => succeed
        }
      }
    }
  }
}
