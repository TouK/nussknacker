import {ActionButtonProps} from "./ActionButton"
import {BuiltinButtonTypes} from "./BuiltinButtonTypes"
import {CustomButtonTypes} from "./CustomButtonTypes"

type GenericButton<T> = {type: T}

type Buttons =
  | GenericButton<BuiltinButtonTypes>
  | GenericButton<CustomButtonTypes.customAction> & ActionButtonProps

export type ToolbarButtonSettings = Omit<Buttons, "type">
export type ToolbarButtonTypes = Buttons["type"]
