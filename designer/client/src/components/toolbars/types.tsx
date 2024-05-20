import { BuiltinButtonTypes, CustomButtonTypes } from "../toolbarSettings/buttons";

export type ToolbarButtonProps = {
    type: BuiltinButtonTypes | CustomButtonTypes;
    name?: string;
    title?: string;
    icon?: string;
    disabled?: boolean;
};
