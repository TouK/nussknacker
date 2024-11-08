/* eslint-disable quote-props */
import { ComponentType, lazy } from "react";
import TipsPanel from "../tips/Tips";
import { CreatorPanel } from "../toolbars/creator/CreatorPanel";
import { DefaultToolbarPanel, ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import ScenarioDetails from "../toolbars/scenarioDetails/ScenarioDetails";
import { UserSettingsPanel } from "../toolbars/UserSettingsPanel";
import ProcessActions from "../toolbars/scenarioActions/ProcessActions";
import { SearchPanel } from "../toolbars/search/SearchPanel";
import { ActivitiesPanel } from "../toolbars/activities";
import { StickyNotesPanel } from "../stickyNotes/StickyNotesPanel";

export const TOOLBAR_COMPONENTS_MAP: Record<string, ComponentType<ToolbarPanelProps>> = {
    DefaultPanel: DefaultToolbarPanel,

    // custom with buttons
    "process-info-panel": ScenarioDetails,
    "process-actions-panel": ProcessActions,
    // no buttons at all
    "tips-panel": TipsPanel,
    "sticky-notes-panel": StickyNotesPanel,
    "creator-panel": CreatorPanel,
    "search-panel": SearchPanel,
    "user-settings-panel": UserSettingsPanel,
    "survey-panel": lazy(() => import("../toolbars/Survey")),
    "activities-panel": ActivitiesPanel,
};
