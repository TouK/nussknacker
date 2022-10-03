import {ToolbarConfig} from "./types"

const isProd = process.env.NODE_ENV === "production"
const isVisualTesting = window["Cypress"]

const isDev = !isProd && !isVisualTesting

export const DEV_TOOLBARS: ToolbarConfig[] = isDev ?
  [
    {id: "user-settings-panel"},
  ] :
  []
