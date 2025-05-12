import i18next from "i18next";

import type { ProcessName } from "../components/Process/types";

export const unsavedProcessChanges = () => {
    return i18next.t("dialogMessages.unsavedProcessChanges", "There are some unsaved scenario changes. Discard unsaved changes?");
};

const deploy = (processName: ProcessName) => {
    return i18next.t("dialogMessages.deploy", "Are you sure you want to deploy {{processName}}?", { processName });
};

const migrate = (processName, environmentId) => {
    return i18next.t("dialogMessages.migrate", "Are you sure you want to migrate {{processName}} to {{environmentId}}?", {
        processName,
        environmentId,
    });
};

const stop = (processName: ProcessName) => {
    return i18next.t("dialogMessages.stop", "Are you sure you want to stop {{processName}}?", { processName });
};

const archiveProcess = (processName: ProcessName) => {
    return i18next.t("dialogMessages.archiveProcess", "Are you sure you want to archive {{processName}}?", { processName });
};

const unArchiveProcess = (processName: ProcessName) => {
    return i18next.t("dialogMessages.unArchiveProcess", "Are you sure you want to unarchive {{processName}}?", { processName });
};

const deleteComment = () => {
    return i18next.t("dialogMessages.deleteComment", "Are you sure you want to delete comment?");
};

const deleteAttachment = (attachmentName: string) => {
    return i18next.t("dialogMessages.deleteAttachment", "Are you sure you want to delete {{attachmentName}} attachment?", {
        attachmentName,
    });
};

const cantArchiveRunningProcess = () => {
    return i18next.t(
        "dialogMessages.cantArchiveRunningProcess",
        "You can\\'t archive running scenario! Stop it first and then click \\'archive\\' button again.",
    );
};

const valueAlreadyTaken = () => {
    return i18next.t("validation.duplicateValue", "This value is already taken");
};

const valueAlreadyTakenDescription = () => {
    return i18next.t("validation.duplicateValueDescription", "Please provide new unique value");
};

export default {
    unsavedProcessChanges,
    deploy,
    migrate,
    stop,
    archiveProcess,
    unArchiveProcess,
    deleteComment,
    deleteAttachment,
    cantArchiveRunningProcess,
    valueAlreadyTaken,
    valueAlreadyTakenDescription,
};
