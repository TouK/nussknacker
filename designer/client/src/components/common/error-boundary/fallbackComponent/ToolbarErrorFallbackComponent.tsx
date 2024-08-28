import { alpha, Box, Button, styled, Typography } from "@mui/material";
import i18next from "i18next";
import React from "react";
import { messages } from "../ErrorBoundary";

const BoxWithIntersectionLines = styled(Box)(({ theme }) => ({
    background: `
    linear-gradient(to top left,
      ${alpha(theme.palette.common.black, 0)} 0%,
      ${alpha(theme.palette.common.black, 0)} calc(50% - 0.8px),
      ${alpha(theme.palette.common.black, 0.3)} 50%,
      ${alpha(theme.palette.common.black, 0)} calc(50% + 0.8px),
      ${alpha(theme.palette.common.black, 0)} 100%),
    linear-gradient(to top right,
      ${alpha(theme.palette.common.black, 0)} 0%,
      ${alpha(theme.palette.common.black, 0)} calc(50% - 0.8px),
      ${alpha(theme.palette.common.black, 0.3)} 50%,
      ${alpha(theme.palette.common.black, 0)} calc(50% + 0.8px),
      ${alpha(theme.palette.common.black, 0)} 100%)
  `,
}));

export const ToolbarErrorFallbackComponent = () => (
    <BoxWithIntersectionLines p={2}>
        <Typography variant={"subtitle1"}>{messages.unexpectedErrorTitle}</Typography>
        <Typography variant={"body2"}>
            {messages.sectionUnavailable} {messages.unexpectedErrorText}
        </Typography>
        <Button
            onClick={() => {
                window.location.reload();
            }}
            data-focus-guard="true"
            focusRipple={false}
            variant={"text"}
            sx={(theme) => ({ marginTop: theme.spacing(1), paddingLeft: 0 })}
        >
            {i18next.t("unexpectedError.reloadButton", "Reload page")}
        </Button>
    </BoxWithIntersectionLines>
);
