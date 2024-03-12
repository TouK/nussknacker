/* eslint-disable quote-props */
import { ComponentType } from "react";
import TipsPanel from "../tips/Tips";
import { AttachmentsPanel } from "../toolbars/AttachmentsPanel";
import { CommentsPanel } from "../comment/CommentsPanel";
import { CreatorPanel } from "../toolbars/creator/CreatorPanel";
import { DefaultToolbarPanel, ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import ScenarioDetails from "../toolbars/scenarioDetails/ScenarioDetails";
import { UserSettingsPanel } from "../toolbars/UserSettingsPanel";
import { VersionsPanel } from "../toolbars/VersionsPanel";
import loadable from "@loadable/component";
import ProcessActions from "../toolbars/actions/ProcessActions";
import { SearchPanel } from "../toolbars/search/SearchPanel";

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
    "survey-panel": loadable(() => import("../toolbars/Survey")),
};
