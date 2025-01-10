import { ActionName, PredefinedActionName, ProcessStateType, Scenario } from "./types";
import { descriptionProcessArchived, unknownDescription, unknownTooltip } from "./messages";

export const unknownIcon = "/assets/states/status-unknown.svg";
const archivedIcon = "/assets/process/archived.svg";

class ProcessStateUtils {
    public canDeploy = (state: ProcessStateType): boolean => state?.allowedActions.includes(PredefinedActionName.Deploy);

    public canCancel = (state: ProcessStateType): boolean => state?.allowedActions.includes(PredefinedActionName.Cancel);

    public canArchive = (state: ProcessStateType): boolean => state?.allowedActions.includes(PredefinedActionName.Archive);

    public canSeeRunOffSchedule = (state: ProcessStateType): boolean => state?.visibleActions.includes(PredefinedActionName.RunOffSchedule);

    public canRunOffSchedule = (state: ProcessStateType): boolean => state?.allowedActions.includes(PredefinedActionName.RunOffSchedule);

    getStateDescription({ isArchived }: Scenario, processState: ProcessStateType): string {
        if (isArchived) {
            return descriptionProcessArchived();
        }

        return processState?.description || unknownDescription();
    }

    getStatusIcon({ isArchived, state }: Scenario, processState: ProcessStateType): string {
        if (isArchived) {
            return archivedIcon;
        }

        return processState?.icon || state?.icon || unknownIcon;
    }

    getStatusTooltip({ isArchived, state }: Scenario, processState: ProcessStateType): string {
        if (isArchived) {
            return descriptionProcessArchived();
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
