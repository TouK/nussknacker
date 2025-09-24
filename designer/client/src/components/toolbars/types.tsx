import type { BuiltinButtonTypes, CustomButtonTypes } from "../toolbarSettings/buttons/buttonsMap";

export type ToolbarButtonProps = {
    type: BuiltinButtonTypes | CustomButtonTypes;
    name?: string;
    title?: string;
    titleOverride?: string;
    icon?: string;
    disabled?: boolean;
};
