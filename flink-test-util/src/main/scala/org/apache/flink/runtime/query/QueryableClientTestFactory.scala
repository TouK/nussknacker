package org.apache.flink.runtime.query

import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import org.apache.flink.runtime.leaderretrieval.StandaloneLeaderRetrievalService
import org.apache.flink.runtime.query.netty.{DisabledKvStateRequestStats, KvStateClient}

import scala.concurrent.duration.FiniteDuration

// This class exists because in flink 1.2 there is no simple way of manually creating org.apache.flink.runtime.query.QueryableStateClient
// and we need it for tests
// This class has flink's package because AkkaKvStateLocationLookupService has package-scope constructor
// In flink 1.3 api changes so it will be probably gone anyway
object QueryableClientTestFactory {

  def apply(actorSystem: ActorSystem, akkaUrl: String = "akka://flink/user/jobmanager_1") = {
    val lookupService = new AkkaKvStateLocationLookupService(
      new StandaloneLeaderRetrievalService(akkaUrl),
      actorSystem,
      FiniteDuration.apply(10, TimeUnit.SECONDS),
      new AkkaKvStateLocationLookupService.FixedDelayLookupRetryStrategyFactory(3, FiniteDuration.apply(1000, "ms"))
    )
    val client = new QueryableStateClient(lookupService, new KvStateClient(1, new DisabledKvStateRequestStats), actorSystem.dispatcher)
    client
  }
}