package pl.touk.nussknacker.engine.util.loader

import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor3}
import org.scalatest.{FlatSpec, Matchers}

class ScalaServiceLoaderSpec extends FlatSpec with Matchers with TableDrivenPropertyChecks {

  behavior of "ScalaServiceLoader.chooseClass"

  trait DummyFactoryTrait
  case class DummyAuthenticatorFactory() extends DummyFactoryTrait
  case class DummyAuthenticatorFactory2() extends DummyFactoryTrait
  case class DummyAuthenticatorFactory3() extends DummyFactoryTrait

  it should "Load class from implementations" in {
    val default = DummyAuthenticatorFactory()
    val dummy2 = DummyAuthenticatorFactory2()
    val dummy3 = DummyAuthenticatorFactory3()

    val table: TableFor3[List[DummyFactoryTrait], DummyFactoryTrait, DummyFactoryTrait] = Table(
      ("class factories", "default factory", "chosen factory"),
      (DummyAuthenticatorFactory() :: Nil, default, default),
      (List(dummy3), default, dummy3),
      (List(dummy3, dummy2), default, dummy3)
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
