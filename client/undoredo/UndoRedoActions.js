
export function undo() {
  return {type: "UNDO"};
}

export function redo() {
  return {type: "REDO"};
}

export function clear() {
  return {type: "CLEAR"};
}