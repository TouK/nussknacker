import { UIActivity } from "../ActivitiesPanel";

export const handleToggleActivities = (
    activities: UIActivity[],
    uiGeneratedId: string,
    sameItemOccurrence: number,
    type: "expand" | "collapse",
) => {
    const newState = [...activities];

    const isHidden = type === "collapse";
    const isClicked = type !== "collapse";

    const buttonIndex = newState.findIndex((uiActivity) => uiActivity.uiGeneratedId === uiGeneratedId);

    if (buttonIndex === -1) return { activities, buttonPosition: -1 };

    let itemsToSetState = sameItemOccurrence;
    let iteration = 0;

    while (itemsToSetState > 0) {
        iteration++;

        const targetIndex = buttonIndex - iteration + 1;

        if (targetIndex < 0 || targetIndex >= newState.length) break;

        const itemToHide = newState[targetIndex];

        if (itemToHide.uiType === "item") {
            newState[targetIndex] = { ...itemToHide, isHidden };
            itemsToSetState--;
        }
    }

    const clickedItem = newState[buttonIndex];

    if (clickedItem.uiType === "toggleItemsButton") {
        newState[buttonIndex] = { ...clickedItem, isClicked };
    }

    return { uiActivities: newState, buttonPosition: buttonIndex - iteration };
};
