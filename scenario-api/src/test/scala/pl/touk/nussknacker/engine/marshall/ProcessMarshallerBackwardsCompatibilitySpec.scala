package pl.touk.nussknacker.engine.marshall

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers

class ProcessMarshallerBackwardsCompatibilitySpec extends AnyFlatSpec with Matchers{

  private val legacyJsonWithNoFields =
    """{
      |  "metaData" : {
      |    "id" : "testId",
      |    "typeSpecificData" : {
      |      "parallelism" : 10,
      |      "spillStateToDisk" : true,
      |      "useAsyncInterpretation" : null,
      |      "checkpointIntervalInSeconds" : 11,
      |      "type" : "StreamMetaData"
      |    },
      |    "additionalFields" : null
      |  },
      |  "nodes" : [],
      |  "additionalBranches" : []
      |}""".stripMargin

  private val newJsonWithNoFields =
    """{
      |  "metaData" : {
      |    "id" : "testId",
      |    "additionalFields" : {
      |       "description": null,
      |       "properties" : {
      |         "parallelism" : "10",
      |         "spillStateToDisk" : "true",
      |         "useAsyncInterpretation" : "",
      |         "checkpointIntervalInSeconds" : "11"
      |       },
      |       "metaDataType": "StreamMetaData"
      |    }
      |  },
      |  "nodes" : [],
      |  "additionalBranches" : []
      |}""".stripMargin

  private val legacyJsonWithDescriptionNoProperties =
    """{
      |  "metaData" : {
      |    "id" : "testId",
      |    "typeSpecificData" : {
      |      "parallelism" : 10,
      |      "spillStateToDisk" : true,
      |      "useAsyncInterpretation" : null,
      |      "checkpointIntervalInSeconds" : 11,
      |      "type" : "StreamMetaData"
      |    },
      |    "additionalFields" : {
      |      "description": "someDescription"
      |    }
      |  },
      |  "nodes" : [],
      |  "additionalBranches" : []
      |}""".stripMargin

  private val newJsonWithDescriptionNoProperties =
    """{
      |  "metaData" : {
      |    "id" : "testId",
      |    "additionalFields" : {
      |       "description": "someDescription",
      |       "properties" : {
      |         "parallelism" : "10",
      |         "spillStateToDisk" : "true",
      |         "useAsyncInterpretation" : "",
      |         "checkpointIntervalInSeconds" : "11"
      |       },
      |       "metaDataType": "StreamMetaData"
      |    }
      |  },
      |  "nodes" : [],
      |  "additionalBranches" : []
      |}""".stripMargin

  private val legacyJsonWithAdditionalProperties =
    """{
      |  "metaData" : {
      |    "id" : "testId",
      |    "typeSpecificData" : {
      |      "parallelism" : 10,
      |      "spillStateToDisk" : true,
      |      "useAsyncInterpretation" : null,
      |      "checkpointIntervalInSeconds" : 11,
      |      "type" : "StreamMetaData"
      |    },
      |    "additionalFields" : {
      |      "description": null,
      |      "properties": {
      |        "someProperty1": "",
      |        "someProperty2": "someValue2"
      |      }
      |    }
      |  },
      |  "nodes" : [],
      |  "additionalBranches" : []
      |}""".stripMargin

  private val newJsonWithAdditionalProperties =
    """{
      |  "metaData" : {
      |    "id" : "testId",
      |    "additionalFields" : {
      |       "description": null,
      |       "properties" : {
      |         "someProperty1": "",
      |         "someProperty2": "someValue2",
      |         "parallelism" : "10",
      |         "spillStateToDisk" : "true",
      |         "useAsyncInterpretation" : "",
      |         "checkpointIntervalInSeconds" : "11"
      |       },
      |       "metaDataType": "StreamMetaData"
      |    }
      |  },
      |  "nodes" : [],
      |  "additionalBranches" : []
      |}""".stripMargin


  it should "decode legacy json with no additional fields" in {
    ProcessMarshaller.fromJson(legacyJsonWithNoFields).isValid shouldBe true
  }

  it should "decode new json with no additional fields" in {
    ProcessMarshaller.fromJson(newJsonWithNoFields).isValid shouldBe true
  }

  it should "decode new json and old with no additional fields to the same structure" in {
    val oldCP = ProcessMarshaller.fromJson(legacyJsonWithNoFields).getOrElse(throw new AssertionError())
    val newCP = ProcessMarshaller.fromJson(newJsonWithNoFields).getOrElse(throw new AssertionError())
    oldCP shouldBe newCP
  }

  it should "decode legacy json with description and no additional properties" in {
    ProcessMarshaller.fromJson(legacyJsonWithDescriptionNoProperties).isValid shouldBe true
  }

  it should "decode new json with description and no additional properties" in {
    ProcessMarshaller.fromJson(newJsonWithDescriptionNoProperties).isValid shouldBe true
  }

  it should "decode new json and old with description and no additional properties to the same structure" in {
    val oldCP = ProcessMarshaller.fromJson(legacyJsonWithDescriptionNoProperties).getOrElse(throw new AssertionError())
    val newCP = ProcessMarshaller.fromJson(newJsonWithDescriptionNoProperties).getOrElse(throw new AssertionError())
    oldCP shouldBe newCP
  }

  it should "decode legacy json with additional properties" in {
    ProcessMarshaller.fromJson(legacyJsonWithAdditionalProperties).isValid shouldBe true
  }

  it should "decode new json with additional properties" in {
    ProcessMarshaller.fromJson(newJsonWithAdditionalProperties).isValid shouldBe true
  }

  it should "decode new json and old with additional properties to the same structure" in {
    val oldCP = ProcessMarshaller.fromJson(legacyJsonWithAdditionalProperties).getOrElse(throw new AssertionError())
    val newCP = ProcessMarshaller.fromJson(newJsonWithAdditionalProperties).getOrElse(throw new AssertionError())
    oldCP shouldBe newCP
  }
}
