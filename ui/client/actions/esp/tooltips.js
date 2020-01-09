export function enableToolTipsHighlight() {
  return {
    type: "SWITCH_TOOL_TIPS_HIGHLIGHT",
    isHighlighted: true,
  }
}

export function disableToolTipsHighlight() {
  return {
    type: "SWITCH_TOOL_TIPS_HIGHLIGHT",
    isHighlighted: false,
  }
}
