import React from "react";
import Dropzone from "react-dropzone";

import { ButtonRoot } from "./ButtonRoot";
import type { ToolbarButtonProps } from "./index";

export const ToolbarButton = React.forwardRef<HTMLDivElement & HTMLButtonElement, ToolbarButtonProps>(function ToolbarButton(
    { onDrop, ...props },
    ref,
) {
    if (onDrop) {
        return (
            <Dropzone disabled={props.disabled} onDrop={onDrop}>
                {({ getRootProps, getInputProps, isDragActive }) => (
                    <>
                        <ButtonRoot ref={ref} {...props} {...getRootProps(props)} isActive={isDragActive} />
                        <input {...getInputProps()} />
                    </>
                )}
            </Dropzone>
        );
    }

    return <ButtonRoot ref={ref} {...props} />;
});
