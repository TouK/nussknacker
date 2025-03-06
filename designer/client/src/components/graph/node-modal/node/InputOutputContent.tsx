import { PropsOf } from "@emotion/react/dist/emotion-react.cjs";
import { Stack } from "@mui/material";
import React, { forwardRef } from "react";
import { ButtonsVariant, ToolbarButtons } from "../../../toolbarComponents/toolbarButtons";
import AdhocTestingButton from "../../../toolbars/test/buttons/AdhocTestingButton";
import { CustomButtonTypes } from "../../../toolbarSettings/buttons";
import { InputOutputLayout } from "../InputOutputLayout";
import { StyledContent } from "./StyledHeader";

export const InputOutputContent = forwardRef<HTMLDivElement, PropsOf<typeof StyledContent>>(function ExtendedContent(props, forwardedRef) {
    return (
        <InputOutputLayout>
            <Stack sx={{ height: "100%", justifyContent: "space-between" }}>
                <Stack sx={{ overflow: "hidden" }}>
                    <ToolbarButtons variant={ButtonsVariant.horizontal} sx={{ padding: 1 }}>
                        <AdhocTestingButton type={CustomButtonTypes.adhocTesting} />
                    </ToolbarButtons>
                    <StyledContent {...props} />
                </Stack>
                <div ref={forwardedRef} />
            </Stack>
        </InputOutputLayout>
    );
});
