import {LinkButtonProps} from "./LinkButton"
import {ActionButtonProps} from "./ActionButton"
import {BuiltinButtonTypes} from "./BuiltinButtonTypes"
import {CustomButtonTypes} from "./CustomButtonTypes"

type GenericButton<T> = {type: T}

type Button =
  | GenericButton<BuiltinButtonTypes>
  | GenericButton<CustomButtonTypes.customAction> & ActionButtonProps
  | GenericButton<CustomButtonTypes.customLink> & LinkButtonProps

export type ToolbarButtonSettings = Omit<Button, "type">
export type ToolbarButtonTypes = Button["type"]
export type ToolbarButton = Button
