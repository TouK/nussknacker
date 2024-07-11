import type { ReactEventHandler } from "react";
import type { DropEvent } from "react-dropzone";
import { BuiltinButtonTypes, CustomButtonTypes } from "../../toolbarSettings/buttons";

export interface ToolbarButtonProps {
    name: string;
    icon: React.JSX.Element | string;
    type: BuiltinButtonTypes | CustomButtonTypes;
    className?: string;
    disabled?: boolean;
    title?: string;
    onDrop?: <T extends File>(acceptedFiles: T[], rejectedFiles: T[], event: DropEvent) => void;
    onMouseOver?: ReactEventHandler;
    onMouseOut?: ReactEventHandler;
    onClick?: ReactEventHandler;
    hasError?: boolean;
    isActive?: boolean;
}
