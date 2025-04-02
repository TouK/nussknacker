import moment from "moment/moment";
import { v4 as uuid4, v5 as uuid5 } from "uuid";

import type { Activity, ButtonActivity, DateActivity, UIActivity } from "../ActivitiesPanel";
import { formatDate } from "./date";

function extractKeys(obj: Record<string, any>, depth = 3): string[] {
    if (!obj || depth < 0) return [];
    const keys = Object.keys(obj);
    if (depth <= 0) return keys;
    return keys.flatMap((key) => [key, ...extractKeys(obj[key], depth - 1)]);
}

const NAMESPACE = "785062d6-79e5-46fc-b41b-3564e02b5c9c";

function generateStableId(obj: Record<string, any>): string {
    const jsonString = JSON.stringify(obj, extractKeys(obj).sort());
    return uuid5(jsonString, NAMESPACE);
}

const createUiActivity = (activity: Activity, previousActivities: UIActivity[] = []) => {
    const uiGeneratedId = generateStableId(activity);
    const previousActivity = previousActivities.find((a) => a.uiGeneratedId === uiGeneratedId);
    const uiActivity: UIActivity = {
        isActiveFound: false,
        isFound: false,
        ...previousActivity,
        ...activity,
        uiGeneratedId,
        uiType: "item",
        isHidden: false,
    };

    if (uiActivity?.attachment?.file?.status === "DELETED") {
        uiActivity.additionalFields.push({ name: "", value: `File '${uiActivity.attachment.filename}' removed` });
    }

    return uiActivity;
};
const getLatestDateItem = (uiActivities: UIActivity[]) => {
    let previousDateItem: DateActivity | undefined;

    for (let prev = uiActivities.length; prev >= 0; prev--) {
        const item = uiActivities[prev];
        if (item?.uiType === "date") {
            previousDateItem = item;
            break;
        }
    }

    return previousDateItem;
};

export const extendActivitiesWithUIData = (activitiesDataWithMetadata: Activity[], previousActivities: UIActivity[] = []) => {
    const uiActivities: UIActivity[] = [];
    const maxAllowedTypesDuplicatesToItemsHide = 2;

    const recursiveDateLabelDesignation = (
        currentActivity: Activity,
        index: number,
        occurrences: string[] = [],
        iteration = 0,
    ): DateActivity | undefined => {
        const nextActivity = activitiesDataWithMetadata[index + 1 + iteration];
        const latestDateItem = getLatestDateItem(uiActivities);
        const currentAndNextActivityTypeAreTheSame = currentActivity.type === nextActivity?.type;

        if (latestDateItem?.value?.includes?.(formatDate(currentActivity.date))) {
            return undefined;
        }

        const isDateRangeInOccurrences = occurrences.every((occurrence) => occurrence === occurrences[0]);
        const shouldAddDateRangeElement =
            occurrences.length > maxAllowedTypesDuplicatesToItemsHide && !currentAndNextActivityTypeAreTheSame && !isDateRangeInOccurrences;

        if (shouldAddDateRangeElement) {
            const dates = occurrences.map((occurrence) => moment(occurrence));
            return {
                uiGeneratedId: uuid4(),
                uiType: "date",
                value: [formatDate(moment.min(dates)), formatDate(moment.max(dates))],
            };
        }

        const currentAndNextActivityDateAreTheSame = formatDate(currentActivity.date) === (nextActivity && formatDate(nextActivity.date));

        if (currentAndNextActivityTypeAreTheSame || currentAndNextActivityDateAreTheSame) {
            iteration++;

            if (currentAndNextActivityTypeAreTheSame) {
                occurrences.push(formatDate(currentActivity.date));
                const isNextActivityLastOfType = activitiesDataWithMetadata[index + 1 + iteration]?.type !== nextActivity.type;
                if (isNextActivityLastOfType) {
                    occurrences.push(formatDate(nextActivity.date));
                }
            } else {
                occurrences = [];
            }

            return recursiveDateLabelDesignation(nextActivity, index, occurrences, iteration);
        }

        const initialActivity = activitiesDataWithMetadata[index];

        const isDateElementPreviouslyAdded = latestDateItem?.value?.includes?.(formatDate(initialActivity.date));
        if (!isDateElementPreviouslyAdded) {
            return {
                uiGeneratedId: uuid4(),
                uiType: "date",
                value: formatDate(initialActivity.date),
            };
        }

        return undefined;
    };

    const recursiveToggleItemsButtonDesignation = (activity: Activity, index: number, occurrences = 0): ButtonActivity | undefined => {
        const previousActivityIndex = index - 1 - occurrences;
        const previousActivity = activitiesDataWithMetadata[previousActivityIndex];
        const nextActivity = activitiesDataWithMetadata[index + 1];

        if (
            occurrences >= maxAllowedTypesDuplicatesToItemsHide &&
            activity.type !== previousActivity?.type &&
            activity.type !== nextActivity?.type
        ) {
            return {
                uiGeneratedId: uuid4(),
                uiType: "toggleItemsButton",
                sameItemOccurrence: occurrences,
                isClicked: false,
            };
        }

        if (activity.type === previousActivity?.type) {
            occurrences++;
            return recursiveToggleItemsButtonDesignation(activity, index, occurrences);
        }

        return undefined;
    };

    const initiallyHideItems = (sameItemOccurrence: number) => {
        const itemOnly = uiActivities.filter((uiActivity) => uiActivity.uiType === "item");
        for (let i = itemOnly.length - sameItemOccurrence; i < itemOnly.length; i++) {
            const item = itemOnly[i];

            if (item.uiType === "item") {
                item.isHidden = true;
            }
        }
    };

    activitiesDataWithMetadata
        .sort((a, b) => moment(b.date).diff(a.date))
        .forEach((activity, index) => {
            const dateLabel = recursiveDateLabelDesignation(activity, index);
            const toggleItemsButton = recursiveToggleItemsButtonDesignation(activity, index);
            dateLabel && uiActivities.push(dateLabel);

            uiActivities.push(createUiActivity(activity, previousActivities));

            if (toggleItemsButton) {
                initiallyHideItems(toggleItemsButton.sameItemOccurrence);
                uiActivities.push(toggleItemsButton);
            }
        });

    return uiActivities;
};
