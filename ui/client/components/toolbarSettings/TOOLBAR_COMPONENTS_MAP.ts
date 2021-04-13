/* eslint-disable quote-props */
import {ComponentType} from "react"
import TipsPanel from "../tips/Tips"
import {AttachmentsPanel} from "../toolbars/AttachmentsPanel"
import {CommentsPanel} from "../toolbars/CommentsPanel"
import {CreatorPanel} from "../toolbars/creator/CreatorPanel"
import {DefaultToolbarPanel, ToolbarPanelProps} from "./DefaultToolbarPanel"
import DetailsPanel from "../toolbars/details/DetailsPanel"
import ProcessInfo from "../toolbars/status/ProcessInfo"
import TestPanel from "../toolbars/test/TestPanel"
import {VersionsPanel} from "../toolbars/VersionsPanel"

export const TOOLBAR_COMPONENTS_MAP: Record<string, ComponentType<ToolbarPanelProps>> = {
  DefaultPanel: DefaultToolbarPanel,

  // only small changes from default
  "TEST-PANEL": TestPanel,

  // custom with buttons
  "PROCESS-INFO": ProcessInfo,

  // no buttons at all
  "DETAILS-PANEL": DetailsPanel,
  "TIPS-PANEL": TipsPanel,
  "CREATOR-PANEL": CreatorPanel,
  "VERSIONS-PANEL": VersionsPanel,
  "COMMENTS-PANEL": CommentsPanel,
  "ATTACHMENTS-PANEL": AttachmentsPanel,
}

