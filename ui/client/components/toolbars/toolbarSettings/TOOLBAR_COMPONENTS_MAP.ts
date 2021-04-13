/* eslint-disable quote-props */
import {ComponentType} from "react"
import TipsPanel from "../../tips/Tips"
import {AttachmentsPanel} from "../AttachmentsPanel"
import {CommentsPanel} from "../CommentsPanel"
import {CreatorPanel} from "../creator/CreatorPanel"
import {DefaultToolbarPanel, ToolbarPanelProps} from "../DefaultToolbarPanel"
import DetailsPanel from "../details/DetailsPanel"
import ProcessInfo from "../status/ProcessInfo"
import TestPanel from "../test/TestPanel"
import {VersionsPanel} from "../VersionsPanel"

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

