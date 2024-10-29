package pl.touk.nussknacker.engine.flink.api.typeinformation

import org.apache.flink.api.common.typeinfo.{TypeInformation, Types}
import org.apache.flink.api.java.typeutils.GenericTypeInfo
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.time.{Instant, LocalDate, LocalDateTime, LocalTime}
import java.sql.{Date, Time, Timestamp}

class FlinkTypeInfoRegistrarTest extends AnyFunSuite with Matchers {

  private val nuTypesMapping: Map[Class[_], TypeInformation[_]] = Map(
    classOf[LocalDate]     -> Types.LOCAL_DATE,
    classOf[LocalTime]     -> Types.LOCAL_TIME,
    classOf[LocalDateTime] -> Types.LOCAL_DATE_TIME,
  )

  private val flinkTypesMapping: Map[Class[_], TypeInformation[_]] = Map(
    classOf[String]               -> Types.STRING,
    classOf[Boolean]              -> Types.BOOLEAN,
    classOf[Byte]                 -> Types.BYTE,
    classOf[Short]                -> Types.SHORT,
    classOf[Integer]              -> Types.INT,
    classOf[Long]                 -> Types.LONG,
    classOf[Float]                -> Types.FLOAT,
    classOf[Double]               -> Types.DOUBLE,
    classOf[Character]            -> Types.CHAR,
    classOf[java.math.BigDecimal] -> Types.BIG_DEC,
    classOf[java.math.BigInteger] -> Types.BIG_INT,
    classOf[Instant]              -> Types.INSTANT,
    classOf[Date]                 -> Types.SQL_DATE,
    classOf[Time]                 -> Types.SQL_TIME,
    classOf[Timestamp]            -> Types.SQL_TIMESTAMP,
  )

  test("Looking for TypeInformation for a NU types should return a GenericTypeInfo") {
    nuTypesMapping.foreach { case (klass, _) =>
      val typeInfo = TypeInformation.of(klass)
      typeInfo shouldBe new GenericTypeInfo(klass)
    }
  }

  test("Looking for TypeInformation for a NU types with registrar should return a specific TypeInformation") {
    FlinkTypeInfoRegistrar.ensureBaseTypesAreRegistered()

    nuTypesMapping.foreach { case (klass, expected) =>
      val typeInfo = TypeInformation.of(klass)
      typeInfo shouldBe expected
    }
  }

  test("Make sure that the other types have specific TypeInformation") {
    flinkTypesMapping.foreach { case (klass, expected) =>
      val typeInfo = TypeInformation.of(klass)
      typeInfo shouldBe expected
    }
  }

}
