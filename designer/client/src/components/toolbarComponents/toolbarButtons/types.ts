import type React from "react";
import type { DropEvent } from "react-dropzone";

import type { BuiltinButtonTypes, CustomButtonTypes } from "../../toolbarSettings/buttons";

type ButtonProps = React.DetailedHTMLProps<React.ButtonHTMLAttributes<HTMLButtonElement>, HTMLButtonElement>;
type DivProps = React.DetailedHTMLProps<React.HTMLAttributes<HTMLDivElement>, HTMLDivElement>;
type ElementProps = ButtonProps & DivProps;

export type ToolbarButtonProps = Omit<ElementProps, "type" | "onDrop"> & {
    name: string;
    icon: React.JSX.Element | string;
    type: BuiltinButtonTypes | CustomButtonTypes;
    onDrop?: <T extends File>(acceptedFiles: T[], rejectedFiles: T[], event: DropEvent) => void;
    hasError?: boolean;
    isActive?: boolean;
};
