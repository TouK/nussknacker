package pl.touk.nussknacker.engine.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import pl.touk.nussknacker.engine.api.MetaDataTestData._

class TypeSpecificDataTest extends AnyFunSuite with Matchers {

  test("convert full type specific data to properties") {
    forAll(fullMetaDataCases) { (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
      typeSpecificData.toProperties shouldBe properties
    }
  }

  test("convert full properties to type specific data") {
    forAll(fullMetaDataCases) { (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
      TypeSpecificData(properties, metaDataName) shouldBe typeSpecificData
    }
  }

  test("convert empty type specific data to corresponding map with empty values") {
    forAll(emptyMetaDataCases) { (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
      typeSpecificData.toProperties shouldBe properties
    }
  }

  test("convert empty properties to empty type specific data with defaults") {
    TypeSpecificData(Map.empty, flinkMetaDataName) shouldBe flinkDefaultTypeData
  }

  test("throw exception for unrecognized type specific name") {
    assertThrows[IllegalStateException](
      TypeSpecificData(flinkFullProperties, "fakeMetaDataName")
    )
  }

  test("convert invalid properties to type specific data") {
    StreamMetaData(flinkInvalidProperties) shouldBe flinkEmptyTypeData
  }

}


