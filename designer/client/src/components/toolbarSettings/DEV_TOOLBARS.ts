import { isDev } from "../../devHelpers";
import { ToolbarConfig } from "./types";

export const DEV_TOOLBARS: ToolbarConfig[] = isDev ? [{ id: "user-settings-panel" }] : [];
