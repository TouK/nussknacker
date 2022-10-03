import {ActionButtonProps} from "./ActionButton"
import {BuiltinButtonTypes} from "./BuiltinButtonTypes"
import {CustomButtonTypes} from "./CustomButtonTypes"
import {LinkButtonProps} from "./LinkButton"

type GenericButton<T, P = unknown> = {type: T, name?: string, title?: string, icon?: string, disabled?: boolean} & P

type Button =
  | GenericButton<BuiltinButtonTypes>
  | GenericButton<CustomButtonTypes.customAction, ActionButtonProps>
  | GenericButton<CustomButtonTypes.customLink, LinkButtonProps>

export type ToolbarButtonTypes = Button["type"]
export type ToolbarButton = Button
