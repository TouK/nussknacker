import { PropsOf } from "@emotion/react/dist/emotion-react.cjs";
import { alpha, Stack } from "@mui/material";
import React, { forwardRef } from "react";
import { ButtonsVariant, ToolbarButtons } from "../../../toolbarComponents/toolbarButtons";
import AdhocTestingButton from "../../../toolbars/test/buttons/AdhocTestingButton";
import { CustomButtonTypes } from "../../../toolbarSettings/buttons";
import { InputOutputLayout } from "./InputOutputLayout";
import { StyledContent } from "../node/StyledHeader";

export const InputOutputContent = forwardRef<HTMLDivElement, PropsOf<typeof StyledContent>>(function ExtendedContent(props, forwardedRef) {
    return (
        <InputOutputLayout>
            <Stack sx={{ height: "100%", justifyContent: "space-between" }}>
                <Stack sx={{ overflow: "hidden" }}>
                    <ToolbarButtons
                        variant={ButtonsVariant.horizontal}
                        sx={(theme) => ({
                            padding: 1,
                            justifyContent: "flex-start",
                            ".toolbarButton-Root": {
                                background: alpha(theme.palette.background.default, 0.4),
                                paddingX: 1,
                                paddingY: 0.25,
                            },
                            ".toolbarButton-Icon": {
                                "&, &>*": {
                                    height: ".8em",
                                    width: ".8em",
                                },
                            },
                            ".toolbarButton-Label": {
                                fontSize: ".6em",
                            },
                        })}
                    >
                        <AdhocTestingButton type={CustomButtonTypes.adhocTesting} />
                    </ToolbarButtons>
                    <StyledContent {...props} />
                </Stack>
                <div ref={forwardedRef} />
            </Stack>
        </InputOutputLayout>
    );
});
