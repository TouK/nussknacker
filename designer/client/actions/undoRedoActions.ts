export const undo = (): UndoRedoActions => ({type: "UNDO"})
export const redo = (): UndoRedoActions => ({type: "REDO"})
export const clear = (): UndoRedoActions => ({type: "CLEAR"})

export type UndoRedoActions =
  | { type: "UNDO" }
  | { type: "REDO" }
  | { type: "CLEAR" }
  | { type: "JUMP_TO_STATE", direction: "PAST" | "FUTURE", index: number }
