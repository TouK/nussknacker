import type { BuiltinButtonTypes, CustomButtonTypes } from "../toolbarSettings/buttons";

export type ToolbarButtonProps = {
    type: BuiltinButtonTypes | CustomButtonTypes;
    name?: string;
    title?: string;
    titleOverride?: boolean;
    icon?: string;
    disabled?: boolean;
};
