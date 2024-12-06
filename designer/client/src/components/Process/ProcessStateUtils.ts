import { ActionName, PredefinedActionName, ProcessStateType, Scenario } from "./types";
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
    public canDeploy = (state: ProcessStateType): boolean => state?.allowedActions.includes(PredefinedActionName.Deploy);

    public canCancel = (state: ProcessStateType): boolean => state?.allowedActions.includes(PredefinedActionName.Cancel);

    public canArchive = (state: ProcessStateType): boolean => state?.allowedActions.includes(PredefinedActionName.Archive);

    public canSeePerformSingleExecution = (state: ProcessStateType): boolean =>
        state?.visibleActions.includes(PredefinedActionName.PerformSingleExecution);

    public canPerformSingleExecution = (state: ProcessStateType): boolean =>
        state?.allowedActions.includes(PredefinedActionName.PerformSingleExecution);

    getStateDescription({ isArchived, isFragment }: Scenario, processState: ProcessStateType): string {
        if (isArchived) {
            return isFragment ? descriptionFragmentArchived() : descriptionProcessArchived();
        }

        if (isFragment) {
            return descriptionFragment();
        }

        return processState?.description || unknownDescription();
    }

    getStatusIcon({ isArchived, isFragment, state }: Scenario, processState: ProcessStateType): string {
        if (isArchived) {
            return archivedIcon;
        }

        if (isFragment) {
            return fragmentIcon;
        }

        return processState?.icon || state?.icon || unknownIcon;
    }

    getStatusTooltip({ isArchived, isFragment, state }: Scenario, processState: ProcessStateType): string {
        if (isArchived) {
            return isFragment ? descriptionFragmentArchived() : descriptionProcessArchived();
        }

        if (isFragment) {
            return descriptionFragment();
        }

        return processState?.tooltip || state?.tooltip || unknownTooltip();
    }

    getTransitionKey({ name, isArchived, isFragment, state }: Scenario, processState: ProcessStateType): string {
        if (isArchived || isFragment) {
            return `${name}`;
        }
        return `${name}-${processState?.icon || state?.icon || unknownIcon}`;
    }

    getActionCustomTooltip(processState: ProcessStateType, actionName: ActionName): string | undefined {
        return processState?.actionTooltips[actionName] || undefined;
    }
}

export default new ProcessStateUtils();
