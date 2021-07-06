import {ToolbarConfig} from "./types"

export const DEV_TOOLBARS: ToolbarConfig[] = process.env.NODE_ENV !== "production" ?
  [
    {id: "user-settings-panel"},
  ] :
  []
