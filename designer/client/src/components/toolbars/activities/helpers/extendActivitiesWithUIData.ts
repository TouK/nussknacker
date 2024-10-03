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
    const hideItemsOptionAvailableLimit = 4;

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
            occurrences.length > hideItemsOptionAvailableLimit && !isNextOccurrence && !isDateRangeInOccurrences;

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

    const recursiveToggleItemsButtonDesignation = (activity: Activity, index: number, occurrence = 0): ButtonActivity | undefined => {
        const previousActivityIndex = index - 1 - occurrence;
        const previousActivity = activitiesDataWithMetadata[previousActivityIndex];
        const nextActivity = activitiesDataWithMetadata[index + 1];

        if (
            occurrence > hideItemsOptionAvailableLimit &&
            activity.type !== previousActivity?.type &&
            activity.type !== nextActivity?.type
        ) {
            return {
                uiGeneratedId: uuid4(),
                uiType: "toggleItemsButton",
                sameItemOccurrence: occurrence,
                isClicked: false,
            };
        }

        if (activity.type === previousActivity?.type) {
            occurrence++;
            return recursiveToggleItemsButtonDesignation(activity, index, occurrence);
        }

        return undefined;
    };

    const initiallyHideItems = (sameItemOccurrence: number) => {
        for (let i = uiActivities.length - sameItemOccurrence; i < uiActivities.length; i++) {
            const item = uiActivities[i];

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
