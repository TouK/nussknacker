package db.migration

import org.scalatest.freespec.AnyFreeSpecLike
import org.scalatest.matchers.should.Matchers
import pl.touk.nussknacker.test.base.db.WithHsqlDbTesting
import pl.touk.nussknacker.test.base.it.NuItTest
import pl.touk.nussknacker.test.config.WithSimplifiedDesignerConfig
import pl.touk.nussknacker.test.utils.domain.TestFactory.newDBIOActionRunner
import slick.jdbc.HsqldbProfile
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.Await
import scala.concurrent.duration.Duration

class V1_056__MigrateActionsAndCommentsToScenarioActivities
    extends AnyFreeSpecLike
    with Matchers
    with NuItTest
    with WithSimplifiedDesignerConfig
    with WithHsqlDbTesting {

//  val comments = new V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition.CommentsDefinitions(HsqldbProfile)
//  testDbRef.db.run(
//    (comments.table ++ V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition.CommentEntityData(
//      id = ???, processId = ???, processVersionId = ???, content = ???, user = ???, impersonatedByIdentity = ???, impersonatedByUsername = ???, createDate = ???
//    )
//  )
  "When data is present in old actions table" - {
    "migrate data to scenario_activities table" in {
      import HsqldbProfile.api._
      val runner = newDBIOActionRunner(testDbRef)
//      session
//        .prepareStatement(
//          """
//          |INSERT INTO public.process_actions (process_version_id, "user", performed_at, build_info, action_name, process_id, comment_id, id, state, created_at, failure_message, impersonated_by_identity, impersonated_by_username) VALUES (12, 'Maciej Cichanowicz', '2024-06-27 15:07:33.015466', null, 'run now', 110, 572, '10954b4f-2d30-478b-afd7-ab27e3da32f4', 'FINISHED', '2024-06-27 15:07:26.554604', null, 'Admin App', 'Admin App');
//          |INSERT INTO public.process_actions (process_version_id, "user", performed_at, build_info, action_name, process_id, comment_id, id, state, created_at, failure_message, impersonated_by_identity, impersonated_by_username) VALUES (12, 'Maciej Cichanowicz', '2024-06-27 15:07:57.080057', null, 'CANCEL', 110, null, 'a7cb8478-055e-4fd5-bfa8-34ee2eb7bba8', 'FINISHED', '2024-06-27 15:07:56.895031', null, null, null);
//          |INSERT INTO public.process_actions (process_version_id, "user", performed_at, build_info, action_name, process_id, comment_id, id, state, created_at, failure_message, impersonated_by_identity, impersonated_by_username) VALUES (12, 'Maciej Cichanowicz', '2024-06-27 15:07:59.845724', e'{
//          |  "nussknacker-buildTime" : "2024-06-23T15:35:35.475741",
//          |  "nussknacker-gitCommit" : "7407c2b36d8af87293c73503a9447977673156a3",
//          |  "nussknacker-name" : "nussknacker-common-api",
//          |  "nussknacker-version" : "1.15.2-preview_1.15-esp-2024-06-23-18932-7407c2b36-SNAPSHOT",
//          |  "process-buildTime" : "2024-06-27T10:55:18.240067",
//          |  "process-gitCommit" : "47dea52bc0ff01da9c3e46e98c3323b3af2ea46d",
//          |  "process-name" : "integration",
//          |  "process-version" : "47dea52bc0ff01da9c3e46e98c3323b3af2ea46d-SNAPSHOT"
//          |}', 'DEPLOY', 110, null, 'ddfcf387-00aa-48e8-a15b-6f265edafebe', 'FINISHED', '2024-06-27 15:07:59.225310', null, null, null);
//          |INSERT INTO public.process_actions (process_version_id, "user", performed_at, build_info, action_name, process_id, comment_id, id, state, created_at, failure_message, impersonated_by_identity, impersonated_by_username) VALUES (13, 'Maciej Cichanowicz', '2024-06-27 15:09:32.114327', e'{
//          |  "nussknacker-buildTime" : "2024-06-23T15:35:35.475741",
//          |  "nussknacker-gitCommit" : "7407c2b36d8af87293c73503a9447977673156a3",
//          |  "nussknacker-name" : "nussknacker-common-api",
//          |  "nussknacker-version" : "1.15.2-preview_1.15-esp-2024-06-23-18932-7407c2b36-SNAPSHOT",
//          |  "process-buildTime" : "2024-06-27T10:55:18.240067",
//          |  "process-gitCommit" : "47dea52bc0ff01da9c3e46e98c3323b3af2ea46d",
//          |  "process-name" : "integration",
//          |  "process-version" : "47dea52bc0ff01da9c3e46e98c3323b3af2ea46d-SNAPSHOT"
//          |}', 'DEPLOY', 110, 574, 'b79e542d-13db-4086-ac98-e733f7fa3f9d', 'FINISHED', '2024-06-27 15:09:31.324140', null, 'Business Config', 'Business Config');
//          |INSERT INTO public.process_actions (process_version_id, "user", performed_at, build_info, action_name, process_id, comment_id, id, state, created_at, failure_message, impersonated_by_identity, impersonated_by_username) VALUES (11, 'Grzegorz Skrobisz', '2024-07-01 21:43:07.873472', null, 'run now', 104, null, '3529bcca-c3ca-4f77-8e8f-00e4e812fa7d', 'FINISHED', '2024-07-01 21:43:01.317651', null, null, null);
//          |"""".stripMargin
//        )
//        .execute()

      val dbOperations = for {
        _ <-
          sqlu"""INSERT INTO processes (name, description, category, processing_type, is_fragment, is_archived, id, created_at, created_by, impersonated_by_identity, impersonated_by_username, latest_version_id, latest_finished_action_id, latest_finished_cancel_action_id, latest_finished_deploy_action_id) VALUES ('2024_Q3_6917_NETFLIX', null, 'BatchPeriodic', 'streaming-batch-periodic', false, false, 141, '2024-09-02 11:01:24.564191', 'Some User', null, null, null, null, null, null);"""
        _ <-
          sqlu"""INSERT INTO process_comments (process_version_id, content, `user`, create_date, id, process_id, impersonated_by_identity, impersonated_by_username) VALUES (2, 'Deployment: komentarz przy deployu', 'admin', '2024-05-21 12:22:49.528439', 480, 104, null, null);"""
        result <- new V1_056__MigrateActionsAndCommentsToScenarioActivitiesDefinition.Migration(
          HsqldbProfile
        ).migrateActions
      } yield (result)
      Await.ready(runner.run(dbOperations), Duration.Inf)
    }
  }

}
