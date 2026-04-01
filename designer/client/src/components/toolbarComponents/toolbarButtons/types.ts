import type React from "react";
import type { DropEvent } from "react-dropzone";

import type { OptionHeader } from "../../graph/node-modal/fragment-input-definition/TypeSelect";
import type { ButtonProgressProps } from "../../toolbars/test/buttons/ButtonProgress";
import type { BuiltinButtonTypes, CustomButtonTypes } from "../../toolbarSettings/buttons/buttonsMap";

type ButtonProps = React.DetailedHTMLProps<React.ButtonHTMLAttributes<HTMLButtonElement>, HTMLButtonElement>;

type BaseButtonProps = Omit<ButtonProps, "type" | "onDrop"> & {
    name: string;
    icon: React.JSX.Element | string;
    type: BuiltinButtonTypes | CustomButtonTypes;
    hasError?: boolean;
    isActive?: boolean;
    showIndicator?: boolean;
};

type LoadingButtonProps = BaseButtonProps & {
    isLoading?: boolean;
    loadingVariant: ButtonProgressProps["variant"];
    loadingProgress: ButtonProgressProps["value"];
};

type FileButtonProps = BaseButtonProps & {
    onDrop: <T extends File>(acceptedFiles: T[], rejectedFiles: T[], event: DropEvent) => void;
};

type PresetsButtonProps<Preset = { value: string; label: string; isDisabled?: boolean }> = BaseButtonProps & {
    presets: Array<Preset | OptionHeader>;
    selected: Preset | null;
    onPresetChange: (value: Preset) => { keepMenuOpen?: boolean } | void;
};

export type ToolbarButtonProps = BaseButtonProps | LoadingButtonProps | FileButtonProps | PresetsButtonProps;
