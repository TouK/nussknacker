package pl.touk.nussknacker.engine.util.loader

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.{TableDrivenPropertyChecks, TableFor3}

import java.net.{URL, URLClassLoader}
import java.nio.file.Files

class ScalaServiceLoaderSpec extends AnyFlatSpec with Matchers with TableDrivenPropertyChecks {

  behavior of "ScalaServiceLoader.chooseClass"

  trait DummyTrait

  object DummyLoader extends LoadClassFromClassLoader {
    override type ClassToLoad = DummyTrait
    override val prettyClassName: String = "DummyTrait"

    override def loadAll(classLoader: ClassLoader): List[ClassToLoad] = ScalaServiceLoader.load[DummyTrait](classLoader)
  }

  trait DummyFactoryTrait
  case object DummyAuthenticatorFactory  extends DummyFactoryTrait
  case object DummyAuthenticatorFactory2 extends DummyFactoryTrait
  case object DummyAuthenticatorFactory3 extends DummyFactoryTrait

  it should "give proper hint" in {
    val tempFile = Files.createTempFile("test", ".jar")
    val loader = new URLClassLoader(
      Array(new URL("http://example.com"), tempFile.toUri.toURL, new URL("file:///shouldNotExist.jar"))
    )

    val exception = intercept[IllegalArgumentException] {
      DummyLoader.justOne(loader)
    }
    exception.getMessage shouldBe s"${DummyLoader.prettyClassName} not found. Jar URLs configured: http://example.com, file:${tempFile.toFile.getAbsolutePath}, file:/shouldNotExist.jar, missing files: /shouldNotExist.jar"
  }

  it should "Load class from implementations" in {
    val table: TableFor3[List[DummyFactoryTrait], DummyFactoryTrait, DummyFactoryTrait] = Table(
      ("class factories", "default factory", "chosen factory"),
      (DummyAuthenticatorFactory :: Nil, DummyAuthenticatorFactory, DummyAuthenticatorFactory),
      (List(DummyAuthenticatorFactory3), DummyAuthenticatorFactory, DummyAuthenticatorFactory3),
      (
        List(DummyAuthenticatorFactory3, DummyAuthenticatorFactory2),
        DummyAuthenticatorFactory,
        DummyAuthenticatorFactory3
      )
    )

    forAll(table) { (factories: List[DummyFactoryTrait], default: DummyFactoryTrait, chosen: DummyFactoryTrait) =>
      {
        try {
          ScalaServiceLoader.chooseClass[DummyFactoryTrait]({ default }, factories) match {
            case loaded: DummyFactoryTrait => chosen shouldBe loaded
          }
        } catch {
          case _: IllegalArgumentException => succeed
        }
      }
    }
  }

}
