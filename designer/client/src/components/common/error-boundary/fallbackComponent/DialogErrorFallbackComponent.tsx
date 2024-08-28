import { Button, styled, Typography } from "@mui/material";
import i18next from "i18next";
import React from "react";
import { messages } from "../ErrorBoundary";
import ErrorOccurred from "../images/error-occurred.svg";

const Root = styled("div")({
    display: "flex",
    flexDirection: "column",
    alignItems: "center",
    textAlign: "center",
    justifyContent: "center",
    width: "100%",
    maxWidth: "700px",
    height: "auto",
    padding: "24px 80px",
    margin: "auto",
});

const StyledProblemOccurred = styled(ErrorOccurred)({
    width: "80%",
    height: "auto",
});

export const DialogErrorFallbackComponent = () => (
    <Root>
        <StyledProblemOccurred />
        <Typography variant={"h5"}>{messages.unexpectedErrorTitle}</Typography>
        <Typography variant={"body1"}>{messages.unexpectedErrorText}</Typography>
        <Button
            onClick={() => {
                window.location.reload();
            }}
            data-focus-guard="true"
            focusRipple={false}
            variant={"text"}
            sx={(theme) => ({ marginTop: theme.spacing(2) })}
        >
            {i18next.t("unexpectedError.reloadButton", "Reload page")}
        </Button>
    </Root>
);
