import { FocusableDiv } from "./focusableDiv";
import { ResizeObserver } from "./resizeObserver";
import React, { forwardRef, useState } from "react";

type PaperComponentProps = {
    id: string;
    onResize: () => void;
};

export const PaperContainer = forwardRef<HTMLDivElement, PaperComponentProps>(function PaperComponent(props, ref) {
    // for now this can't use theme nor other dynamic props to maintain reference with jointjs.
    const [stiffProps] = useState(props);

    return (
        <ResizeObserver
            as={FocusableDiv}
            sx={{
                width: "100%",
                height: "100%",
                minHeight: "300px",
                minWidth: "300px",
                userSelect: "none",
            }}
            {...stiffProps}
            ref={ref}
        />
    );
});
