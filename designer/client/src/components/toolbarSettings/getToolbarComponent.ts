/* eslint-disable quote-props */
import type { ComponentType } from "react";
import { lazy } from "react";

import TipsPanel from "../tips/Tips";
import type { ToolbarPanelProps } from "../toolbarComponents/ButtonsToolbar";
import { ButtonsToolbar } from "../toolbarComponents/ButtonsToolbar";
import { ActivitiesPanel } from "../toolbars/activities";
import { CreatorPanel } from "../toolbars/creator/CreatorPanel";
import ProcessActions from "../toolbars/scenarioActions/ProcessActions";
import ScenarioDetails from "../toolbars/scenarioDetails/ScenarioDetails";
import ScenarioStatusPanel from "../toolbars/scenarioDetails/ScenarioStatusPanel";
import { SearchPanel } from "../toolbars/search/SearchPanel";
import { UserSettingsPanel } from "../toolbars/UserSettingsPanel";
import { HorizontalButtonsToolbar } from "./HorizontalButtonsToolbar";
import type { ToolbarConfig } from "./types";

export function getToolbarComponent(config?: ToolbarConfig): ComponentType<ToolbarPanelProps> {
    switch (config?.id) {
        case "scenario-status-panel":
            return ScenarioStatusPanel;
        case "process-info-panel":
            return ScenarioDetails;
        case "process-actions-panel":
            return ProcessActions;
        case "tips-panel":
            return TipsPanel;
        case "creator-panel":
        case "creator-panel2":
            return CreatorPanel;
        case "search-panel":
            return SearchPanel;
        case "user-settings-panel":
            return UserSettingsPanel;
        case "survey-panel":
            return lazy(() => import("../toolbars/Survey"));
        case "activities-panel":
            return ActivitiesPanel;
        default:
            return ButtonsToolbar;
    }
}

export function getToolbarHorizontalComponent(config?: ToolbarConfig): ComponentType<ToolbarPanelProps> | null {
    if (config?.buttons?.length) return HorizontalButtonsToolbar;
    return null;
}
