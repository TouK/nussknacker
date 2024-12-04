import React, { CSSProperties, memo, useEffect, useMemo, useRef } from "react";
import { DateItem, ActivityItem, ToggleButtonItem } from "./ActivityPanelRowItem";
import { UIActivity } from "./ActivitiesPanel";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { RootState } from "../../../reducers";
import { getProcessState } from "../../../reducers/selectors/scenarioState";
import { ActivityItemProvider } from "./ActivityPanelRowItem/ActivityItemProvider";

interface Props {
    index: number;
    style?: CSSProperties | undefined;
    setRowHeight: (index: number, height: number) => void;
    handleShowRows(uiGeneratedId: string, sameItemOccurrence: number): void;
    handleHideRows(uiGeneratedId: string, sameItemOccurrence: number): void;
    activities: UIActivity[];
    searchQuery: string;
}

export const ActivitiesPanelRow = memo(({ index, style, setRowHeight, handleShowRows, handleHideRows, activities, searchQuery }: Props) => {
    const scenarioState = useSelector((state: RootState) => getProcessState(state));

    const { t } = useTranslation();
    const rowRef = useRef<HTMLDivElement>(null);
    const activity = useMemo(() => activities[index], [activities, index]);
    const firstDeployedIndex = useMemo(
        () => activities.findIndex((activeItem) => activeItem.uiType === "item" && activeItem.type === "SCENARIO_DEPLOYED"),
        [activities],
    );
    const scenarioStatusesToActiveDeploy = ["RUNNING", "SCHEDULED"];
    const isDeploymentActive = firstDeployedIndex === index && scenarioStatusesToActiveDeploy.includes(scenarioState.status.name);
    const isFirstDateItem = activities.findIndex((activeItem) => activeItem.uiType === "date") === index;

    useEffect(() => {
        if (rowRef.current) {
            setRowHeight(index, rowRef.current.clientHeight);
        }
    }, [index, rowRef, setRowHeight]);

    const itemToRender = useMemo(() => {
        switch (activity.uiType) {
            case "item": {
                return (
                    <ActivityItemProvider>
                        <ActivityItem activity={activity} ref={rowRef} isDeploymentActive={isDeploymentActive} searchQuery={searchQuery} />
                    </ActivityItemProvider>
                );
            }
            case "date": {
                return <DateItem activity={activity} ref={rowRef} isFirstDateItem={isFirstDateItem} />;
            }
            case "toggleItemsButton": {
                return (
                    <div ref={rowRef}>
                        {activity.isClicked ? (
                            <ToggleButtonItem handleHideRow={() => handleHideRows(activity.uiGeneratedId, activity.sameItemOccurrence)}>
                                {t("activitiesPanel.buttons.showLess", "Show less")}
                            </ToggleButtonItem>
                        ) : (
                            <ToggleButtonItem handleHideRow={() => handleShowRows(activity.uiGeneratedId, activity.sameItemOccurrence)}>
                                {t("activitiesPanel.buttons.showMore", "Show {{sameItemOccurrence}} more", {
                                    sameItemOccurrence: activity.sameItemOccurrence,
                                })}
                            </ToggleButtonItem>
                        )}
                    </div>
                );
            }
            default: {
                return null;
            }
        }
    }, [activity, handleHideRows, handleShowRows, isDeploymentActive, isFirstDateItem, searchQuery, t]);

    return (
        <div style={style} data-testid={`activity-row-${index}`}>
            {itemToRender}
        </div>
    );
});

ActivitiesPanelRow.displayName = "ActivitiesPanelRow";
