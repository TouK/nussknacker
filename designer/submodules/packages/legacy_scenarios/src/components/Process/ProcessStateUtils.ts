import { ProcessStateType, ProcessType } from "./types";
import { descriptionProcessArchived, descriptionFragment, descriptionFragmentArchived, unknownTooltip } from "./messages";
import { absoluteBePath } from "../../common/UrlUtils";

export function getStatusIcon(
    { isArchived, isFragment, state }: ProcessType,
    processState: ProcessStateType,
    isStateLoaded: boolean,
): string {
    if (isArchived) {
        return absoluteBePath("/assets/process/archived.svg");
    }

    if (isFragment) {
        return absoluteBePath("/assets/process/fragment.svg");
    }

    if (isStateLoaded) {
        return absoluteBePath(processState?.icon || "/assets/states/status-unknown.svg");
    }

    return absoluteBePath(state?.icon || "/assets/states/status-unknown.svg");
}

export function getStatusTooltip(
    { isArchived, isFragment, state }: ProcessType,
    processState: ProcessStateType,
    isStateLoaded: boolean,
): string {
    if (isArchived) {
        return isFragment ? descriptionFragmentArchived() : descriptionProcessArchived();
    }

    if (isFragment) {
        return descriptionFragment();
    }

    if (isStateLoaded) {
        return processState?.tooltip || unknownTooltip();
    }

    return state?.tooltip || unknownTooltip();
}
