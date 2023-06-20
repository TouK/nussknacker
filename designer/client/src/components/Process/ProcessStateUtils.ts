import { ActionType, ProcessStateType, ProcessType } from "./types";
import {
    descriptionProcessArchived,
    descriptionSubprocess,
    descriptionSubprocessArchived,
    unknownDescription,
    unknownTooltip,
} from "./messages";

export const unknownIcon = "/assets/states/status-unknown.svg";
const subprocessIcon = "/assets/process/subprocess.svg";
const archivedIcon = "/assets/process/archived.svg";

class ProcessStateUtils {
    public canDeploy = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Deploy);

    public canCancel = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Cancel);

    public canArchive = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Archive);

    getStateDescription({ isArchived, isSubprocess }: ProcessType, processState: ProcessStateType): string {
        if (isArchived) {
            return isSubprocess ? descriptionSubprocessArchived() : descriptionProcessArchived();
        }

        if (isSubprocess) {
            return descriptionSubprocess();
        }

        return processState?.description || unknownDescription();
    }

    getStatusIcon({ isArchived, isSubprocess, state }: ProcessType, processState: ProcessStateType): string {
        if (isArchived) {
            return archivedIcon;
        }

        if (isSubprocess) {
            return subprocessIcon;
        }

        return processState?.icon || state?.icon || unknownIcon;
    }

    getStatusTooltip({ isArchived, isSubprocess, state }: ProcessType, processState: ProcessStateType): string {
        if (isArchived) {
            return isSubprocess ? descriptionSubprocessArchived() : descriptionProcessArchived();
        }

        if (isSubprocess) {
            return descriptionSubprocess();
        }

        return processState?.tooltip || state?.tooltip || unknownTooltip();
    }

    getTransitionKey({ id, isArchived, isSubprocess, state }: ProcessType, processState: ProcessStateType): string {
        if (isArchived || isSubprocess) {
            return `${id}`;
        }
        return `${id}-${processState?.icon || state?.icon || unknownIcon}`;
    }
}

export default new ProcessStateUtils();
