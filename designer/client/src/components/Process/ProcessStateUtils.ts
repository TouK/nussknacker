import { formatDateTime } from "../../common/DateUtils";
import { memoizeByArgsWithTTL } from "../../helpers/memoizeByArgsWithTTL";
import { WrapAllMethods } from "../../WrapAllMethods";
import { descriptionProcessArchived, unknownDescription, unknownTooltip } from "./messages";
import type { ActionName, ProcessStateType, Scenario } from "./types";
import { isStatusRunning } from "./types";
import { PredefinedActionName } from "./types";
export const unknownIcon = "/assets/states/status-unknown.svg";
const archivedIcon = "/assets/process/archived.svg";

@WrapAllMethods(memoizeByArgsWithTTL)
class ProcessStateUtils {
    canSeeDeploy = (state: ProcessStateType): boolean => this.visibleActions(state).includes(PredefinedActionName.Deploy);

    canDeploy = (state: ProcessStateType): boolean => this.allowedActions(state).includes(PredefinedActionName.Deploy);

    canSeeRedeploy = (state: ProcessStateType): boolean => this.visibleActions(state).includes(PredefinedActionName.Redeploy);

    canRedeploy = (state: ProcessStateType): boolean => this.allowedActions(state).includes(PredefinedActionName.Redeploy);

    canCancel = (state: ProcessStateType): boolean => this.allowedActions(state).includes(PredefinedActionName.Cancel);

    canArchive = (state: ProcessStateType): boolean => this.allowedActions(state).includes(PredefinedActionName.Archive);

    canSeeRunOffSchedule = (state: ProcessStateType): boolean => this.visibleActions(state).includes(PredefinedActionName.RunOffSchedule);

    canRunOffSchedule = (state: ProcessStateType): boolean => this.allowedActions(state).includes(PredefinedActionName.RunOffSchedule);

    private visibleActions = (state: ProcessStateType) => state?.visibleActions || [];

    private allowedActions = (state: ProcessStateType) => state?.allowedActions || [];

    getStateDescription({ isArchived }: Scenario, processState: ProcessStateType): string {
        if (isArchived) {
            return descriptionProcessArchived();
        }

        if (processState?.status && isStatusRunning(processState?.status)) {
            const values: Record<string, string> = {
                versionId: processState.status.versionId,
                startedAt: formatDateTime(processState.status.startedAt),
            };
            return renderHandlebarsTemplate(processState.description, values);
        } else {
            return processState?.description || unknownDescription();
        }
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

export function renderHandlebarsTemplate(template: string, values: Record<string, string>): string {
    return template.replace(/{{\s*(\w+)\s*}}/g, (_, key) => {
        return values[key] ?? "";
    });
}
