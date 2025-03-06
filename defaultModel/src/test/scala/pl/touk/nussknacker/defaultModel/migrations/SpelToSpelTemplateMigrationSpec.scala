package pl.touk.nussknacker.defaultModel.migrations

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.{MetaData, StreamMetaData}

class SpelToSpelTemplateMigrationSpec extends AnyFunSuite with Matchers {

  test("should migrate nodes") {
    val metaData = MetaData("test", StreamMetaData(Some(1)))

  }

}
