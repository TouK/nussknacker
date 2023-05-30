package pl.touk.nussknacker.engine.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import pl.touk.nussknacker.engine.api.MetaDataTestData._

class TypeSpecificDataTest extends AnyFunSuite with Matchers {

  test("create properties from full TypeSpecificData") {
    forAll(fullMetaDataCases) { (properties: Map[String, String], _, typeSpecificData: TypeSpecificData) =>
      typeSpecificData.toProperties shouldBe properties
    }
  }

  test("create TypeSpecificData from full properties") {
    forAll(fullMetaDataCases) { (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
      TypeSpecificData(properties, metaDataName) shouldBe typeSpecificData
    }
  }

  test("create empty properties from empty TypeSpecificData") {
    forAll(emptyMetaDataCases) { (properties: Map[String, String], _, typeSpecificData: TypeSpecificData) =>
      typeSpecificData.toProperties shouldBe properties
    }
  }

  test("throw exception for unrecognized type specific name") {
    assertThrows[IllegalStateException](
      TypeSpecificData(flinkFullProperties, "fakeMetaDataName")
    )
  }

  test("create empty TypeSpecificData from invalid properties") {
    StreamMetaData(flinkInvalidTypeProperties) shouldBe flinkEmptyTypeData
  }

}


