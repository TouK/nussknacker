package pl.touk.nussknacker.ui.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.File
import java.nio.file.Path
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

class ExtraScriptsListingPreparerTest extends AnyFunSuite with Matchers {

  private val classLoader = {
    val classpathEntry = new File(getClass.getResource("/extra-scripts-test").getFile).toURI.toURL
    new URLClassLoader(Seq(classpathEntry), getClass.getClassLoader)
  }

  test("should prepare correct listing based on classpath files") {
    val listingPreparer = new ExtraScriptsListingPreparer(
      classLoader, Path.of("web-extra"), Path.of("/resources-root"))

    listingPreparer.webResourcesListing shouldEqual Seq("/resources-root/a.js", "/resources-root/b.js")
  }

  test("should return empty list for not existing directory") {
    val listingPreparer = new ExtraScriptsListingPreparer(
      classLoader, Path.of("not-existing"), Path.of("/"))

    listingPreparer.webResourcesListing shouldBe empty
  }

  test("should return empty list for root as which is not a directory") {
    val listingPreparer = new ExtraScriptsListingPreparer(
      classLoader, Path.of("a.js"), Path.of("/"))

    listingPreparer.webResourcesListing shouldBe empty
  }

  test("should use scripts.lst file when available") {
    val classLoader = {
      val classpathEntry = new File(getClass.getResource("/extra-scripts-test-lst").getFile).toURI.toURL
      new URLClassLoader(Seq(classpathEntry), getClass.getClassLoader)
    }
    val listingPreparer = new ExtraScriptsListingPreparer(
      classLoader, Path.of("web-extra"), Path.of("/"))

    listingPreparer.webResourcesListing shouldBe Seq("/a.js", "/b.js")
  }

  test("should list resources as of html scripts") {
    val listingPreparer = new ExtraScriptsListingPreparer(
      classLoader, Path.of("web-extra"), Path.of("/"))

    listingPreparer.scriptsListing shouldEqual
      """<script src="/a.js"></script>
        |<script src="/b.js"></script>""".stripMargin
  }

}
