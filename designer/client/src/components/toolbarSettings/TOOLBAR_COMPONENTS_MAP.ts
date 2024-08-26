/* eslint-disable quote-props */
import { ComponentType, lazy } from "react";
import TipsPanel from "../tips/Tips";
import { AttachmentsPanel } from "../toolbars/AttachmentsPanel";
import { CommentsPanel } from "../comment/CommentsPanel";
import { CreatorPanel } from "../toolbars/creator/CreatorPanel";
import { DefaultToolbarPanel, ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import ScenarioDetails from "../toolbars/scenarioDetails/ScenarioDetails";
import { UserSettingsPanel } from "../toolbars/UserSettingsPanel";
import { VersionsPanel } from "../toolbars/VersionsPanel";
import ProcessActions from "../toolbars/scenarioActions/ProcessActions";
import { SearchPanel } from "../toolbars/search/SearchPanel";
import { ActivitiesPanel } from "../toolbars/ActivitiesPanel";

export const TOOLBAR_COMPONENTS_MAP: Record<string, ComponentType<ToolbarPanelProps>> = {
    DefaultPanel: DefaultToolbarPanel,

    // custom with buttons
    "process-info-panel": ScenarioDetails,
    "process-actions-panel": ProcessActions,
    // no buttons at all
    "tips-panel": TipsPanel,
    "creator-panel": CreatorPanel,
    "search-panel": SearchPanel,
    "versions-panel": VersionsPanel,
    "comments-panel": CommentsPanel,
    "attachments-panel": AttachmentsPanel,
    "user-settings-panel": UserSettingsPanel,
    "survey-panel": lazy(() => import("../toolbars/Survey")),
    "activities-panel": ActivitiesPanel,
};
