import i18next from "i18next";
import { ProcessName } from "src/components/Process/types";

export const unsavedProcessChanges = () => {
    return i18next.t("dialogMessages.unsavedProcessChanges", "There are some unsaved scenario changes. Discard unsaved changes?");
};

export const deploy = (processName: ProcessName) => {
    return i18next.t("dialogMessages.deploy", "Are you sure you want to deploy {{processName}}?", { processName });
};

export const migrate = (processName, environmentId) => {
    return i18next.t("dialogMessages.migrate", "Are you sure you want to migrate {{processName}} to {{environmentId}}?", {
        processName,
        environmentId,
    });
};

export const stop = (processName: ProcessName) => {
    return i18next.t("dialogMessages.stop", "Are you sure you want to stop {{processName}}?", { processName });
};

export const archiveProcess = (processName: ProcessName) => {
    return i18next.t("dialogMessages.archiveProcess", "Are you sure you want to archive {{processName}}?", { processName });
};

export const unArchiveProcess = (processName: ProcessName) => {
    return i18next.t("dialogMessages.unArchiveProcess", "Are you sure you want to unarchive {{processName}}?", {
        processName,
    });
};

export const deleteComment = () => {
    return i18next.t("dialogMessages.deleteComment", "Are you sure you want to delete comment?");
};

export const deleteAttachment = (attachmentName: string) => {
    return i18next.t("dialogMessages.deleteAttachment", "Are you sure you want to delete {{attachmentName}} attachment?", {
        attachmentName,
    });
};

export const cantArchiveRunningProcess = () => {
    return i18next.t(
        "dialogMessages.cantArchiveRunningProcess",
        "You can't archive running scenario! Stop it first and then click 'archive' button again.",
    );
};

export const valueAlreadyTaken = () => {
    return i18next.t("validation.duplicateValue", "This value is already taken");
};

export const valueAlreadyTakenDescription = () => {
    return i18next.t("validation.duplicateValueDescription", "Please provide new unique value");
};
