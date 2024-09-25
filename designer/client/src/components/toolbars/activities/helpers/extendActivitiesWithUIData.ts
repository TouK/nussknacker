import moment from "moment/moment";
import { v4 as uuid4 } from "uuid";
import { Activity, ButtonActivity, DateActivity, UIActivities } from "../ActivitiesPanel";

export const extendActivitiesWithUIData = (activitiesDataWithMetadata: Activity[]) => {
    const infiniteListData: UIActivities[] = [];
    const hideItemsOptionAvailableLimit = 4;

    function formatDate(date: string) {
        const now = moment(); // Current date and time
        const inputDate = moment(date); // Date to be formatted

        if (inputDate.isSame(now, "day")) {
            return "Today";
        } else if (inputDate.isSame(moment().subtract(1, "days"), "day")) {
            return "Yesterday";
        } else {
            return inputDate.format("YYYY-MM-DD");
        }
    }

    const recursiveDateLabelDesignation = (activity: Activity, index: number, occurrence = 0): DateActivity | undefined => {
        const nextActivity = activitiesDataWithMetadata[index + 1 + occurrence];
        const previousActivity = activitiesDataWithMetadata[index - 1 + occurrence];

        if (occurrence > hideItemsOptionAvailableLimit && activity.type !== nextActivity?.type) {
            return {
                uiGeneratedId: uuid4(),
                uiType: "date",
                value: `${formatDate(previousActivity.date)} - ${formatDate(activity.date)}`,
            };
        }

        if (activity.type === nextActivity?.type) {
            occurrence++;
            return recursiveDateLabelDesignation(activity, index, occurrence);
        }

        if (
            activity.type !== nextActivity?.type &&
            moment(activity.date).format("YYYY-MM-DD") !==
                (previousActivity?.date ? moment(previousActivity.date).format("YYYY-MM-DD") : undefined)
        ) {
            return {
                uiGeneratedId: uuid4(),
                uiType: "date",
                value: formatDate(activity.date),
            };
        }

        return undefined;
    };

    const recursiveMoreItemsButtonDesignation = (activity: Activity, index: number, occurrence = 0): ButtonActivity | undefined => {
        const previousActivityIndex = index - 1 - occurrence;
        const previousActivity = activitiesDataWithMetadata[previousActivityIndex];
        if (occurrence > hideItemsOptionAvailableLimit && activity.type !== previousActivity?.type) {
            return {
                uiGeneratedId: uuid4(),
                uiType: "moreItemsButton",
                sameItemOccurrence: occurrence,
                isClicked: false,
            };
        }

        if (activity.type === previousActivity?.type) {
            occurrence++;
            return recursiveMoreItemsButtonDesignation(activity, index, occurrence);
        }

        return undefined;
    };

    const initiallyHideItems = () => {
        for (let i = infiniteListData.length - 1 - hideItemsOptionAvailableLimit; i < infiniteListData.length; i++) {
            const item = infiniteListData[i];

            if (item.uiType === "item") {
                item.isHidden = true;
            }
        }
    };

    activitiesDataWithMetadata
        .sort((a, b) => moment(b.date).diff(a.date))
        .forEach((activity, index) => {
            const dateLabel = recursiveDateLabelDesignation(activity, index);
            const moreItemsButton = recursiveMoreItemsButtonDesignation(activity, index);
            dateLabel && infiniteListData.push(dateLabel);
            infiniteListData.push({
                ...activity,
                isActiveFound: false,
                isFound: false,
                uiGeneratedId: uuid4(),
                uiType: "item",
                isHidden: false,
            });
            if (moreItemsButton) {
                initiallyHideItems();
                infiniteListData.push(moreItemsButton);
            }
        });

    return infiniteListData;
};
