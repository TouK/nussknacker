import type { UiSettingsAction } from "./nk/assignSettings";
import type { LoggedUserAction } from "./nk/assignUser";
import type { ProcessDefinitionDataAction } from "./nk/processDefinitionData";

export type SettingsActions = LoggedUserAction | UiSettingsAction | ProcessDefinitionDataAction;
