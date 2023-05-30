package pl.touk.nussknacker.restmodel.process

import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.MetaDataTestData._
import org.scalatest.prop.TableDrivenPropertyChecks._
import pl.touk.nussknacker.engine.api.{ProcessAdditionalFields, TypeSpecificData}
import pl.touk.nussknacker.restmodel.displayedgraph.ProcessProperties

class ProcessPropertiesTest extends AnyFunSuite with Matchers {

  private val id = "Id"

  test("throw exception when creating ProcessProperties with non-matching TypeSpecificData and AdditionalFields") {
    forAll(fullMetaDataCases) {
      (_, _, typeSpecificData: TypeSpecificData) => {
        assertThrows[IllegalStateException](
          ProcessProperties(typeSpecificProperties = typeSpecificData, additionalFields = None))
      }
    }
  }

  test ("construct ProcessProperties from only TypeSpecificData and convert to MetaData") {
    forAll(fullMetaDataCases) {
      (fullProperties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) => {
        val processProperties = ProcessProperties(typeSpecificProperties = typeSpecificData)

        processProperties.propertiesType shouldBe metaDataName
        processProperties.additionalFields.properties shouldBe fullProperties

        val metaData = processProperties.toMetaData(id)

        metaData.typeSpecificData shouldBe typeSpecificData
        metaData.additionalFields.get shouldBe ProcessAdditionalFields(None, fullProperties)
      }
    }
  }

  test("construct TypeSpecificData from correctly duplicated TypeSpecificData and AdditionalFields to ProcessProperties and back") {
    forAll(fullMetaDataCases) {
      (fullProperties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) => {
        // meta data => process properties
        val processProperties = ProcessProperties(
          typeSpecificProperties = typeSpecificData,
          additionalFields = Some(ProcessAdditionalFields(None, typeSpecificData.toProperties)))

        processProperties.propertiesType shouldBe metaDataName
        processProperties.additionalFields.properties shouldBe fullProperties

        // process properties => meta data
        val metaData = processProperties.toMetaData(id)
        metaData.typeSpecificData shouldBe typeSpecificData
        metaData.additionalFields shouldBe Option(ProcessAdditionalFields(None, fullProperties))
      }
    }
  }

  test("construct TypeSpecificData with other properties by and convert to MetaData") {
    forAll(fullMetaDataCases) {
      (fullProperties: Map[String, String], metaDataName: String, typeSpecificData: TypeSpecificData) => {
        // meta data => process properties
        val processProperties = ProcessProperties(
          typeSpecificProperties = typeSpecificData,
          additionalFields = Some(ProcessAdditionalFields(None, fullProperties ++ nonTypeSpecificProperties)))

        processProperties.propertiesType shouldBe metaDataName
        processProperties.additionalFields.properties shouldBe fullProperties ++ nonTypeSpecificProperties

        // process properties => meta data
        val metaData = processProperties.toMetaData(id)
        metaData.typeSpecificData shouldBe typeSpecificData
        metaData.additionalFields shouldBe Some(ProcessAdditionalFields(None, fullProperties ++ nonTypeSpecificProperties))
      }
    }
  }

  test("throw exception when properties have different values") {

    val fullTypeSpecificDataWithInvalidPropertiesCases = Table(
      ("invalidProperties", "metaDataName", "typeSpecificData"),
      (flinkInvalidTypeProperties, flinkMetaDataName, flinkFullTypeData),
      (liteStreamInvalidTypeProperties, liteStreamMetaDataName, liteStreamFullTypeData)
    )

    forAll(fullTypeSpecificDataWithInvalidPropertiesCases) {
      (invalidProperties: Map[String, String], _, fullTypeSpecificData: TypeSpecificData) => {
        assertThrows[IllegalStateException] {
          ProcessProperties(
            typeSpecificProperties = fullTypeSpecificData,
            additionalFields = Some(ProcessAdditionalFields(None, invalidProperties)))
        }
      }
    }
  }

}
