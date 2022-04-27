TRUNCATE TABLE "process_attachments";
ALTER TABLE "process_attachments" DROP COLUMN "file_path";
ALTER TABLE "process_attachments" ADD COLUMN "data" BLOB not null;