package pl.touk.nussknacker.engine.kafka.admin

import org.apache.kafka.clients.admin.Admin

sealed trait CreatedKafkaAdminClient

object CreatedKafkaAdminClient {
  final case class Value(admin: Admin)          extends CreatedKafkaAdminClient
  final case class Failed(exception: Throwable) extends CreatedKafkaAdminClient
}

class CachedKafkaAdminClient(create: => Admin)
