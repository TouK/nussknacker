package pl.touk.nussknacker.ui.process.test

import io.circe.Json
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.engine.api.Context
import pl.touk.nussknacker.engine.schemedkafka.AvroUtils
import pl.touk.nussknacker.engine.testmode.TestProcess.TestResults
import pl.touk.nussknacker.ui.processreport.NodeCount

class TestResultsJsonEncoderTest extends AnyFunSuite with Matchers {

  private val schema = AvroUtils.parseSchema(s"""{
                                                |  "type": "record",
                                                |  "namespace": "pl.touk.nussknacker.engine.schemedkafka",
                                                |  "name": "FullName",
                                                |  "fields": [
                                                |    { "name": "name", "type": "string" },
                                                |    { "name": "age", "type": "int" }
                                                |  ]
                                                |}
    """.stripMargin)

  private val record = AvroUtils.createRecord(schema, Map("name" -> "lcl", "age" -> 18))

  private val results = ResultsWithCounts(
    results = TestResults(
      nodeResults = Map(
        "source" -> List(Context("src", Map("input" -> record)))
      ),
      invocationResults = Map.empty,
      externalInvocationResults = Map.empty,
      exceptions = List.empty
    ),
    counts = Map(
      "input" -> NodeCount(1, 0)
    )
  )

  test("result converter w/o ClassLoader should convert result to string") {
    val jsonEncoderTest = new TestResultsJsonEncoder(EmptyClassLoader)
    val encodedResults  = jsonEncoderTest.encode(results)

    // Avro record is converted to string by running .toString method, there are no errors because in the tests all classes are on the classpath
    val expectedResults = prepareExpectedResponse(Json.fromString("{\"name\": \"lcl\", \"age\": 18}"))

    encodedResults shouldBe expectedResults
  }

  test("result converter with ClassLoader should convert result to proper json object") {
    val jsonEncoderTest = new TestResultsJsonEncoder(getClass.getClassLoader)
    val encodedResults  = jsonEncoderTest.encode(results)

    // Avro record is converted by AvroToJsonEncoder, as a result we get JSON
    val expectedResults = prepareExpectedResponse(
      Json.fromFields(
        Map(
          "name" -> Json.fromString("lcl"),
          "age"  -> Json.fromInt(18)
        )
      )
    )

    encodedResults shouldBe expectedResults
  }

  private def prepareExpectedResponse(input: Json): Json =
    Json.fromFields(
      Map(
        "results" -> Json.fromFields(
          Map(
            "nodeResults" -> Json.fromFields(
              Map(
                "source" -> Json.fromValues(
                  List(
                    Json.fromFields(
                      Map(
                        "id" -> Json.fromString("src"),
                        "variables" -> Json.fromFields(
                          Map(
                            "input" -> Json.fromFields(
                              Map(
                                "pretty" -> input
                              )
                            )
                          )
                        )
                      )
                    )
                  )
                )
              )
            ),
            "invocationResults"         -> Json.fromFields(Map.empty),
            "externalInvocationResults" -> Json.fromFields(Map.empty),
            "exceptions"                -> Json.fromValues(List.empty),
          )
        ),
        "counts" -> Json.fromFields(
          Map(
            "input" -> Json.fromFields(
              Map(
                "all"            -> Json.fromInt(1),
                "errors"         -> Json.fromInt(0),
                "fragmentCounts" -> Json.fromFields(Map.empty)
              )
            )
          )
        )
      )
    )

  private object EmptyClassLoader extends ClassLoader(null)

}
