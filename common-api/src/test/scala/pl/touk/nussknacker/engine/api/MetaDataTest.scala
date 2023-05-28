package pl.touk.nussknacker.engine.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.MetaDataTestData._
import org.scalatest.prop.TableDrivenPropertyChecks._

class MetaDataTest extends AnyFunSuite with Matchers {

  test("convert properties of only type specific data to metadata") {
    forAll(fullMetaDataCases) {
      (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
        val metaData = MetaData("Id", ProcessAdditionalFields(None, properties), metaDataName)

        metaData.typeSpecificData shouldBe typeSpecificData
        metaData.additionalFields shouldBe Some(ProcessAdditionalFields(None, typeSpecificData.toProperties))
    }
  }

  test("convert properties of type specific data with additional ones to metadata") {
    forAll(fullMetaDataCases) {
      (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
        val mergedProperties = nonTypeSpecificProperties ++ properties
        val metaData = MetaData("Id", ProcessAdditionalFields(None, mergedProperties), metaDataName)

        metaData.typeSpecificData shouldBe typeSpecificData
        metaData.additionalFields shouldBe Some(ProcessAdditionalFields(None, typeSpecificData.toProperties ++ nonTypeSpecificProperties))
    }
  }

  test("convert properties of invalid type specific data to metadata") {
    forAll(invalidMetaDataCases) {
      (properties: Map[String, String], metaDataName: String, emptyTypeSpecificData: TypeSpecificData) =>
        val metaData = MetaData("Id", ProcessAdditionalFields(None, properties), metaDataName)

        metaData.typeSpecificData shouldBe emptyTypeSpecificData
        metaData.additionalFields shouldBe Option(ProcessAdditionalFields(None, properties))
    }
  }

  test("throw exception for unrecognized metadata name") {
    assertThrows[IllegalStateException](
      MetaData("Id", ProcessAdditionalFields(None, flinkFullProperties), "fakeMetaDataName")
    )
  }

}
