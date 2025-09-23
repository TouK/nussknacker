import type { BuiltinButtonTypes } from "../toolbarSettings/buttons/BuiltinButtonTypes";
import type { CustomButtonTypes } from "../toolbarSettings/buttons/CustomButtonTypes";

export type ToolbarButtonProps = {
    type: BuiltinButtonTypes | CustomButtonTypes;
    name?: string;
    title?: string;
    titleOverride?: string;
    icon?: string;
    disabled?: boolean;
};
