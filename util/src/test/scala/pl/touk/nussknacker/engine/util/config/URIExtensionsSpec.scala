package pl.touk.nussknacker.engine.util.config

import org.scalatest.{FlatSpec, Matchers}

import java.net.URI

class URIExtensionsSpec extends FlatSpec with URIExtensions with Matchers {

  it should ("default to a file when no scheme has been provided") in {
    URI.create("/absolute/path").withFileSchemeDefault shouldBe URI.create("file:///absolute/path")
    URI.create("relative/path").withFileSchemeDefault shouldBe URI.create(s"file://${System.getProperty("user.dir")}/relative/path")
  }

  it should ("leave the URI intact when any scheme has been provided") in {
    URI.create("anyscheme:scheme/specific").withFileSchemeDefault shouldBe URI.create(s"anyscheme:scheme/specific")
  }

}
