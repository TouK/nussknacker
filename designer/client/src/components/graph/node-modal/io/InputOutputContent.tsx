import type { PropsOf } from "@emotion/react/dist/emotion-react.cjs";
import { Stack } from "@mui/material";
import React, { forwardRef } from "react";

import { ValueDragPreview } from "../../../ValueDragPreview";
import { StyledContent } from "../node/StyledHeader";
import { InputOutputLayout } from "./InputOutputLayout";

export const InputOutputContent = forwardRef<HTMLDivElement, PropsOf<typeof StyledContent>>(function ExtendedContent(props, forwardedRef) {
    return (
        <InputOutputLayout>
            <Stack sx={{ height: "100%", justifyContent: "space-between" }}>
                <Stack sx={{ overflow: "hidden" }}>
                    <ValueDragPreview>
                        <StyledContent {...props} />
                    </ValueDragPreview>
                </Stack>
                <div ref={forwardedRef} />
            </Stack>
        </InputOutputLayout>
    );
});
