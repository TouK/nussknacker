import { Box } from "@mui/material";
import React from "react";

import { ShowPasswordsButton } from "../../../common/copyToClipboard/ShowPasswordsButton";
import { InlineButtonsWrapper } from "../../../common/InlineButtonsWrapper";
import { useTextSanitizer } from "./useTextSanitizer";

type PasswordMaskProps = {
    children: string;
    timeout?: number;
};

const PasswordMask = ({ children }: PasswordMaskProps) => {
    const { visible, setVisible, sanitize } = useTextSanitizer();
    return (
        <>
            <Box
                component="span"
                sx={(theme) => ({
                    fontFamily: "monospace",
                    color: theme.palette.primary.main,
                })}
            >
                {sanitize(children)}
            </Box>
            <InlineButtonsWrapper>
                <ShowPasswordsButton onClick={() => setVisible((v) => !v)} visible={visible} />
            </InlineButtonsWrapper>
        </>
    );
};

export default PasswordMask;
