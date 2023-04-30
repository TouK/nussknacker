package pl.touk.nussknacker.ui.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.{File, FilenameFilter}
import scala.reflect.internal.util.ScalaClassLoader.URLClassLoader

class ExtraScriptsListingPreparerTest extends AnyFunSuite with Matchers {

  private val classLoaderUsingFiles = {
    val classpathEntry = new File(getClass.getResource("/extra-scripts-test").getFile).toURI.toURL
    new URLClassLoader(Seq(classpathEntry), getClass.getClassLoader)
  }

  private val classLoaderUsingResources = {
    new URLClassLoader(Seq(getClass.getResource("/extra-scripts-test")), getClass.getClassLoader)
  }

  test("should prepare correct listing based on classpath files") {
    val listingPreparer = new ExtraScriptsListingPreparer(
      classLoaderUsingFiles, "/web-root/extra-scripts-root")

    listingPreparer.webResourcesListing shouldEqual Seq("/web-root/extra-scripts-root/a.js", "/web-root/extra-scripts-root/b.js")
  }

  test("should prepare correct listing based on classpath resources") {
    val listingPreparer = new ExtraScriptsListingPreparer(
      classLoaderUsingResources, "/web-root/extra-scripts-root")

    listingPreparer.webResourcesListing shouldEqual Seq("/web-root/extra-scripts-root/a.js", "/web-root/extra-scripts-root/b.js")
  }

  test("should return empty list for not existing directory") {
    val listingPreparer = new ExtraScriptsListingPreparer(
      classLoaderUsingFiles, "not-existing")

    listingPreparer.webResourcesListing shouldBe empty
  }

  test("should thrown an exception for extra root which is not a directory") {
    val listingPreparer = new ExtraScriptsListingPreparer(
      classLoaderUsingFiles, "/a.js")

    an[IllegalStateException] shouldBe thrownBy {
      listingPreparer.webResourcesListing
    }
  }

  test("should list resources as of html scripts") {
    val listingPreparer = new ExtraScriptsListingPreparer(
      classLoaderUsingFiles, "web-root/extra-scripts-root")

    listingPreparer.scriptsListing shouldEqual
      """<script src="/web-root/extra-scripts-root/a.js"></script>
        |<script src="/web-root/extra-scripts-root/b.js"></script>""".stripMargin
  }

}
