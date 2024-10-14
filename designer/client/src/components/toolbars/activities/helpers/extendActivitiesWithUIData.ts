import moment from "moment/moment";
import { v4 as uuid4 } from "uuid";
import { Activity, ButtonActivity, DateActivity, UIActivity } from "../ActivitiesPanel";
import { formatDate } from "./date";

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

export const extendActivitiesWithUIData = (activitiesDataWithMetadata: Activity[]) => {
    const uiActivities: UIActivity[] = [];
    const hideItemsOptionAvailableLimit = 3;

    const recursiveDateLabelDesignation = (
        currentActivity: Activity,
        index: number,
        occurrences: string[] = [],
        iteration = 0,
    ): DateActivity | undefined => {
        const nextActivity = activitiesDataWithMetadata[index + 1 + iteration];
        const latestDateItem = getLatestDateItem(uiActivities);

        if (latestDateItem?.value?.includes?.(formatDate(currentActivity.date))) {
            return undefined;
        }

        const isDateRangeInOccurrences = occurrences.every((occurrence) => occurrence === occurrences[0]);
        const isNextOccurrence = currentActivity.type === nextActivity?.type;
        const shouldAddDateRangeElement =
            occurrences.length >= hideItemsOptionAvailableLimit && !isNextOccurrence && !isDateRangeInOccurrences;

        if (shouldAddDateRangeElement) {
            const dates = occurrences.map((occurrence) => moment(occurrence));
            return {
                uiGeneratedId: uuid4(),
                uiType: "date",
                value: [formatDate(moment.min(dates)), formatDate(moment.max(dates))],
            };
        }

        const currentAndNextActivityDateAreTheSame = formatDate(currentActivity.date) === (nextActivity && formatDate(nextActivity.date));

        if (currentAndNextActivityDateAreTheSame || isNextOccurrence) {
            iteration++;

            if (isNextOccurrence) {
                occurrences.push(formatDate(currentActivity.date));
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
            occurrences >= hideItemsOptionAvailableLimit &&
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
            uiActivities.push({
                ...activity,
                isActiveFound: false,
                isFound: false,
                uiGeneratedId: uuid4(),
                uiType: "item",
                isHidden: false,
            });
            if (toggleItemsButton) {
                initiallyHideItems(toggleItemsButton.sameItemOccurrence);
                uiActivities.push(toggleItemsButton);
            }
        });

    return uiActivities;
};
