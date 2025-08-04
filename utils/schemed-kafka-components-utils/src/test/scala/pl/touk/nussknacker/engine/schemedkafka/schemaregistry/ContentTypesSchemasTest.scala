package pl.touk.nussknacker.engine.schemedkafka.schemaregistry

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

class ContentTypesSchemasTest extends AnyFunSuite with Matchers {

  test("schemaForJson is distinct from schemaForPlain") {
    ContentTypesSchemas.schemaForJson shouldNot be(ContentTypesSchemas.schemaForPlain)
  }

}
