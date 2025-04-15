import type { BuiltinButtonTypes, CustomButtonTypes } from "../toolbarSettings/buttons";

export type Preset = {
    value: string;
    label: string;
};

export type ToolbarButtonProps = {
    type: BuiltinButtonTypes | CustomButtonTypes;
    name?: string;
    title?: string;
    icon?: string;
    disabled?: boolean;
    presets?: Preset[];
};
