import type { CSSProperties } from "react";
import React, { memo, useEffect, useMemo, useRef } from "react";
import { useTranslation } from "react-i18next";

import { isDeploymentActivity } from "../../../reducers/selectors/activities";
import { getIsRunningOrScheduled } from "../../../reducers/selectors/scenarioState";
import { useAppSelector } from "../../../store/storeHelpers";
import type { UIActivity } from "./ActivitiesPanel";
import { ActivityItem } from "./ActivityPanelRowItem/ActivityItem";
import { ActivityItemProvider } from "./ActivityPanelRowItem/ActivityItemProvider";
import { DateItem } from "./ActivityPanelRowItem/DateItem";
import { ToggleButtonItem } from "./ActivityPanelRowItem/ToggleButtonItem";

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
    const isRunning = useAppSelector(getIsRunningOrScheduled);

    const { t } = useTranslation();
    const rowRef = useRef<HTMLDivElement>(null);
    const activity = useMemo(() => activities[index], [activities, index]);
    const firstDeployedIndex = useMemo(() => activities.findIndex(isDeploymentActivity), [activities]);
    const isDeploymentActive = firstDeployedIndex === index && isRunning;
    const isFirstDateItem = activities.findIndex((activeItem) => activeItem.uiType === "date") === index;

    useEffect(() => {
        const node = rowRef.current;
        if (!node) return;
        setRowHeight(index, node.clientHeight);
        if (typeof ResizeObserver === "undefined") return;
        const observer = new ResizeObserver(() => setRowHeight(index, node.clientHeight));
        observer.observe(node);
        return () => observer.disconnect();
    }, [index, setRowHeight]);

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
