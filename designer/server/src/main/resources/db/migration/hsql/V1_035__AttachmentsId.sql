ALTER TABLE "process_attachments" ADD COLUMN "new_id" INTEGER GENERATED BY DEFAULT AS IDENTITY(START WITH 1, INCREMENT BY 1);
ALTER TABLE "process_attachments" DROP PRIMARY KEY;
ALTER TABLE "process_attachments" DROP COLUMN "id";
ALTER TABLE "process_attachments" ALTER COLUMN "new_id" RENAME TO "id";
ALTER TABLE "process_attachments" ADD PRIMARY KEY ("id");

