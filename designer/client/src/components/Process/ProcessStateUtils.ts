import { ActionType, ProcessStateType, ProcessType } from "./types";
import {
    descriptionProcessArchived,
    descriptionFragment,
    descriptionFragmentArchived,
    unknownDescription,
    unknownTooltip,
} from "./messages";

export const unknownIcon = "/assets/states/status-unknown.svg";
const fragmentIcon = "/assets/process/fragment.svg";
const archivedIcon = "/assets/process/archived.svg";

class ProcessStateUtils {
    public canDeploy = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Deploy);

    public canCancel = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Cancel);

    public canArchive = (state: ProcessStateType): boolean => state?.allowedActions.includes(ActionType.Archive);

    getStateDescription({ isArchived, isFragment }: ProcessType, processState: ProcessStateType): string {
        if (isArchived) {
            return isFragment ? descriptionFragmentArchived() : descriptionProcessArchived();
        }

        if (isFragment) {
            return descriptionFragment();
        }

        return processState?.description || unknownDescription();
    }

    getStatusIcon({ isArchived, isFragment, state }: ProcessType, processState: ProcessStateType): string {
        if (isArchived) {
            return archivedIcon;
        }

        if (isFragment) {
            return fragmentIcon;
        }

        return processState?.icon || state?.icon || unknownIcon;
    }

    getStatusTooltip({ isArchived, isFragment, state }: ProcessType, processState: ProcessStateType): string {
        if (isArchived) {
            return isFragment ? descriptionFragmentArchived() : descriptionProcessArchived();
        }

        if (isFragment) {
            return descriptionFragment();
        }

        return processState?.tooltip || state?.tooltip || unknownTooltip();
    }

    getTransitionKey({ id, isArchived, isFragment, state }: ProcessType, processState: ProcessStateType): string {
        if (isArchived || isFragment) {
            return `${id}`;
        }
        return `${id}-${processState?.icon || state?.icon || unknownIcon}`;
    }
}

export default new ProcessStateUtils();
