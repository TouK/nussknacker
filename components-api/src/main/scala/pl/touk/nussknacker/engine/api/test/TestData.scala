package pl.touk.nussknacker.engine.api.test

import java.nio.charset.StandardCharsets

case class TestData(testData: Array[Byte], rowLimit: Int)

object TestData {
  def newLineSeparated(s: String*): TestData = new TestData(s.mkString("\n").getBytes(StandardCharsets.UTF_8), s.length)
}