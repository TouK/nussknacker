import { BuiltinButtonTypes } from "../toolbarSettings/buttons";

export type ToolbarButtonProps = {
    type?: BuiltinButtonTypes;
    name?: string;
    title?: string;
    icon?: string;
    disabled?: boolean;
};
