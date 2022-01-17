package pl.touk.nussknacker.engine.util.loader

import org.scalatest.{FunSuite, Matchers}

import java.net.{URL, URLClassLoader}

class ModelClassLoaderSpec extends FunSuite with Matchers {

  test("should detect nested URLs in classloader") {

    def resource(file: String): URL = getClass.getResource("/modelClassLoader" + file)
    val nonFileUrl = new URL("http://dummy.com")

    val urls = List(resource(""), nonFileUrl)

    val loader = ModelClassLoader(urls)

    //we're not using .jar to avoid messing with .gitignore
    val expected = Set(
      resource("/first.jara"),
      resource("/a/second.jara"),
      resource("/b/c/fourth.jara"),
      resource("/b/third.jara"),
      nonFileUrl
    )
    loader.classLoader.asInstanceOf[URLClassLoader].getURLs.toSet shouldBe expected
    loader.urls.toSet shouldBe expected
  }

}
