package pl.touk.nussknacker.engine.api

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.MetaDataTestData._
import org.scalatest.prop.TableDrivenPropertyChecks._

class MetaDataTest extends AnyFunSuite with Matchers {

  private val id = "Id"

  // Tests for constructor used for conversion from DisplayableProcess to CanonicalProcess (ProcessProperties -> MetaData)
  test("create MetaData from full correct properties") {
    forAll(fullMetaDataCases) {
      (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
        val metaData = MetaData(id, ProcessAdditionalFields(None, properties), metaDataName)

        metaData.typeSpecificData shouldBe typeSpecificData
        metaData.additionalFields shouldBe Some(ProcessAdditionalFields(None, typeSpecificData.toProperties))
    }
  }

  test("create MetaData from full correct properties and additional ones") {
    forAll(fullMetaDataCases) {
      (properties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) =>
        val mergedProperties = nonTypeSpecificProperties ++ properties
        val metaData = MetaData(id, ProcessAdditionalFields(None, mergedProperties), metaDataName)

        metaData.typeSpecificData shouldBe typeSpecificData
        metaData.additionalFields shouldBe Some(ProcessAdditionalFields(None, typeSpecificData.toProperties ++ nonTypeSpecificProperties))
    }
  }

  test("create MetaData from invalid properties") {
    forAll(invalidTypeAndEmptyMetaDataCases) {
      (properties: Map[String, String], metaDataName: String, emptyTypeSpecificData: TypeSpecificData) =>
        val metaData = MetaData(id, ProcessAdditionalFields(None, properties), metaDataName)

        metaData.typeSpecificData shouldBe emptyTypeSpecificData
        metaData.additionalFields shouldBe Some(ProcessAdditionalFields(None, properties))
    }
  }

  test("throw exception when trying to create MetaData with properties not matching to type") {
    forAll(fullMetaDataCases) {
      (_, metaDataName: String, _) =>
        assertThrows[IllegalStateException](
          MetaData(id, ProcessAdditionalFields(None, Map("nonexistentProperty" -> "value")), metaDataName)
        )
    }
  }

  // Tests for constructor used during creation of CanonicalProcess
  test("construct MetaData from matching TypeSpecificData and properties") {
    forAll(fullMetaDataCases) {
      (properties: Map[String, String], _, fullTypeSpecificData: TypeSpecificData) =>
        val metaData = MetaData(id, typeSpecificData = fullTypeSpecificData, Some(ProcessAdditionalFields(None, properties)))

        metaData.typeSpecificData shouldBe fullTypeSpecificData
        metaData.additionalFields shouldBe Some(ProcessAdditionalFields(None, properties))
    }
  }

  test("construct MetaData from empty TypeSpecificData and invalid properties") {
    forAll(invalidTypeAndEmptyMetaDataCases) {
      (invalidProperties: Map[String, String], _, emptyTypeSpecificData: TypeSpecificData) =>
        val metaData = MetaData(id, emptyTypeSpecificData, Some(ProcessAdditionalFields(None, invalidProperties)))

        metaData.typeSpecificData shouldBe emptyTypeSpecificData
        metaData.additionalFields shouldBe Some(ProcessAdditionalFields(None, invalidProperties))
    }
  }

  test("throw exception when trying to create MetaData with contradictory properties") {
    forAll(invalidValuesMetaDataCases) {
      (differentValuesProperties: Map[String, String], _, fullTypeSpecificData: TypeSpecificData) =>
        assertThrows[IllegalStateException](
          MetaData(id, fullTypeSpecificData, Some(ProcessAdditionalFields(None, differentValuesProperties)))
        )
    }
  }

  test("throw exception for unrecognized metadata name") {
    assertThrows[IllegalStateException](
      MetaData(id, ProcessAdditionalFields(None, flinkFullProperties), "fakeMetaDataName")
    )
  }

  test("construct MetaData from only TypeSpecificData") {
    forAll(fullMetaDataCases) {
      (properties: Map[String, String], _, fullTypeSpecificData: TypeSpecificData) =>
        val metaDataFromConvenienceConstructor = MetaData(id, typeSpecificData = fullTypeSpecificData)
        val metaDataFromExplicitConstructor = MetaData(id, typeSpecificData = fullTypeSpecificData, Some(ProcessAdditionalFields(None, properties)))

        metaDataFromConvenienceConstructor.typeSpecificData shouldBe fullTypeSpecificData
        metaDataFromConvenienceConstructor.additionalFields shouldBe Some(ProcessAdditionalFields(None, properties))

        metaDataFromConvenienceConstructor shouldBe metaDataFromExplicitConstructor
    }
  }

}
