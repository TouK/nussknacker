package pl.touk.nussknacker.engine.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import org.scalatest.prop.TableDrivenPropertyChecks.forAll
import pl.touk.nussknacker.engine.api.TypeSpecificDataTestData._

class TypeSpecificDataTest extends AnyFunSuite with Matchers {

  private val testId = "Id"

  test("create MetaData from full properties") {
    forAll(fullMetaDataCases) {
      (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
        val metaData = MetaData(testId, ProcessAdditionalFields(None, properties, metaDataName))
        metaData.typeSpecificData shouldBe typeSpecificData
    }
  }

  test("create MetaData from full properties and additional ones") {
    forAll(fullMetaDataCases) {
      (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
        val mergedProperties = nonTypeSpecificProperties ++ properties
        val metaData         = MetaData(testId, ProcessAdditionalFields(None, mergedProperties, metaDataName))
        metaData.typeSpecificData shouldBe typeSpecificData
        metaData.additionalFields.properties shouldBe mergedProperties
    }
  }

  test("create empty MetaData from invalid properties") {
    forAll(invalidTypeAndEmptyMetaDataCases) {
      (properties: Map[String, String], metaDataName: String, emptyTypeSpecificData: TypeSpecificData) =>
        val metaData = MetaData(testId, ProcessAdditionalFields(None, properties, metaDataName))
        metaData.typeSpecificData shouldBe emptyTypeSpecificData
        metaData.additionalFields.properties shouldBe properties
    }
  }

  test("create empty MetaData from no properties") {
    forAll(invalidTypeAndEmptyMetaDataCases) { (_, metaDataName: String, emptyTypeSpecificData: TypeSpecificData) =>
      val metaData = MetaData(testId, ProcessAdditionalFields(None, Map.empty, metaDataName))
      metaData.typeSpecificData shouldBe emptyTypeSpecificData
    }
  }

  test("throw exception for creating typeSpecificData with unrecognized metadata name") {
    assertThrows[IllegalStateException](
      MetaData(testId, ProcessAdditionalFields(None, flinkFullProperties, "fakeMetaDataName")).typeSpecificData
    )
  }

  test("should create map from TypeSpecificData") {
    forAll(fullMetaDataCases) { (properties: Map[String, String], _, typeSpecificData: TypeSpecificData) =>
      typeSpecificData.toMap shouldBe properties
    }
    forAll(emptyMetaDataCases) { (properties: Map[String, String], _, emptyTypeSpecificData: TypeSpecificData) =>
      emptyTypeSpecificData.toMap shouldBe properties
    }
  }

}
