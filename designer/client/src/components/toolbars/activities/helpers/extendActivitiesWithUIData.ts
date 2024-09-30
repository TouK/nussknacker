import moment from "moment/moment";
import { v4 as uuid4 } from "uuid";
import { Activity, ButtonActivity, DateActivity, UIActivity } from "../ActivitiesPanel";
import { formatDate } from "./date";

const getPreviousDateItem = (index: number, uiActivities: UIActivity[]) => {
    let previousDateItem: DateActivity | undefined;

    for (let prev = index; prev >= 0; prev--) {
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
        activity: Activity,
        index: number,
        occurrences: string[] = [],
        iteration = 0,
    ): DateActivity | undefined => {
        const nextActivity = activitiesDataWithMetadata[index + 1 + iteration];
        const previousDateItem = getPreviousDateItem(index, uiActivities);

        if (previousDateItem?.value?.includes?.(moment(activity.date).format("YYYY-MM-DD"))) {
            return undefined;
        }

        const shouldAddDateRangeElement = occurrences.length > hideItemsOptionAvailableLimit && activity.type !== nextActivity?.type;

        if (shouldAddDateRangeElement) {
            const dates = occurrences.map((occurrence1) => moment(occurrence1));
            return {
                uiGeneratedId: uuid4(),
                uiType: "date",
                value: `${moment.min(dates).format("YYYY-MM-DD")} - ${moment.max(dates).format("YYYY-MM-DD")}`,
            };
        }

        const currentAndNextActivityDateAreTheSame =
            moment(activity.date).format("YYYY-MM-DD") === (nextActivity && moment(nextActivity.date).format("YYYY-MM-DD"));
        const currentAndNextActivityAreTheSame = activity.type === nextActivity?.type;

        if (currentAndNextActivityDateAreTheSame || currentAndNextActivityAreTheSame) {
            iteration++;

            if (currentAndNextActivityAreTheSame) {
                occurrences.push(activity.date);
            }

            return recursiveDateLabelDesignation(nextActivity, index, occurrences, iteration);
        }

        const isDateElementPreviouslyAdded = previousDateItem?.value?.includes?.(moment(activity.date).format("YYYY-MM-DD"));
        if (!isDateElementPreviouslyAdded) {
            return {
                uiGeneratedId: uuid4(),
                uiType: "date",
                value: formatDate(activity.date),
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
