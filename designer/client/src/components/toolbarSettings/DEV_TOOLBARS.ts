import { isDev } from "../../devHelpers";
import { ToolbarConfig } from "./types";

export const getDevToolbars = (userSettingsVisible?: boolean): ToolbarConfig[] =>
    isDev || userSettingsVisible ? [{ id: "user-settings-panel" }] : [];
