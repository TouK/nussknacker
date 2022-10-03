/* eslint-disable quote-props */
import {ComponentType} from "react"
import TipsPanel from "../tips/Tips"
import {AttachmentsPanel} from "../toolbars/AttachmentsPanel"
import {CommentsPanel} from "../toolbars/CommentsPanel"
import {CreatorPanel} from "../toolbars/creator/CreatorPanel"
import {DefaultToolbarPanel, ToolbarPanelProps} from "../toolbarComponents/DefaultToolbarPanel"
import ProcessInfo from "../toolbars/status/ProcessInfo"
import {UserSettingsPanel} from "../toolbars/UserSettingsPanel"
import {VersionsPanel} from "../toolbars/VersionsPanel"

export const TOOLBAR_COMPONENTS_MAP: Record<string, ComponentType<ToolbarPanelProps>> = {
  DefaultPanel: DefaultToolbarPanel,

  // custom with buttons
  "process-info-panel": ProcessInfo,

  // no buttons at all
  "tips-panel": TipsPanel,
  "creator-panel": CreatorPanel,
  "versions-panel": VersionsPanel,
  "comments-panel": CommentsPanel,
  "attachments-panel": AttachmentsPanel,
  "user-settings-panel": UserSettingsPanel,
}

