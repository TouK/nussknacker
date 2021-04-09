type ToggleToolboxGroupAction = { type: "TOGGLE_NODE_TOOLBOX_GROUP", nodeGroup: string }

export function toggleToolboxGroup(nodeGroup: string): ToggleToolboxGroupAction {
  return {
    type: "TOGGLE_NODE_TOOLBOX_GROUP",
    nodeGroup,
  }
}

export type ToolboxActions =
  | ToggleToolboxGroupAction
