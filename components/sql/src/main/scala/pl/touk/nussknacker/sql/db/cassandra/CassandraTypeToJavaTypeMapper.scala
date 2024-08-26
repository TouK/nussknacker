package pl.touk.nussknacker.sql.db.cassandra

import jdk.jshell.spi.ExecutionControl.NotImplementedException

// implementation based on documentation comment from the top of CassandraResultSet class.
// In case cassandra driver changes this class needs to be changed accordingly.
object CassandraTypeToJavaTypeMapper {

  def getJavaTypeFromCassandraType(cassandraType: String): Class[_] = {
    cassandraType match {
      case "ascii"     => classOf[java.lang.String]
      case "bigint"    => classOf[java.lang.Long]
      case "blob"      => classOf[java.nio.ByteBuffer]
      case "boolean"   => classOf[java.lang.Boolean]
      case "counter"   => classOf[java.lang.Long]
      case "date"      => classOf[java.sql.Date]
      case "decimal"   => classOf[java.math.BigDecimal]
      case "double"    => classOf[java.lang.Double]
      case "duration"  => classOf[com.datastax.oss.driver.api.core.data.CqlDuration]
      case "float"     => classOf[java.lang.Float]
      case "inet"      => classOf[java.net.InetAddress]
      case "int"       => classOf[java.lang.Integer]
      case "list"      => classOf[java.util.List[_]]
      case "map"       => classOf[java.util.Map[_, _]]
      case "set"       => classOf[java.util.Set[_]]
      case "smallint"  => classOf[java.lang.Short]
      case "text"      => classOf[java.lang.String]
      case "time"      => classOf[java.sql.Time]
      case "timestamp" => classOf[java.sql.Timestamp]
      case "timeuuid"  => classOf[java.util.UUID]
      case "tinyint"   => classOf[java.lang.Byte]
      case "tuple"     => classOf[com.datastax.oss.driver.api.core.data.TupleValue]
      case "udt"       => classOf[com.datastax.oss.driver.api.core.data.UdtValue]
      case "uuid"      => classOf[java.util.UUID]
      case "varchar"   => classOf[java.lang.String]
      case "varint"    => classOf[java.math.BigInteger]
      case "vector"    => classOf[com.datastax.oss.driver.api.core.data.CqlVector[_]]
      case _           => throw new NotImplementedException("Unknown or not implemented in cassandra driver cql type")
    }
  }

}
