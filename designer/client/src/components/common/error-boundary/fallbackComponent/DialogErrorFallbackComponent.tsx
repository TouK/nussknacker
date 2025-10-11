import { Box, Button, styled, Typography } from "@mui/material";
import type { WindowContentProps } from "@touk/window-manager";
import { DefaultContent, useWindowContext } from "@touk/window-manager";
import type { PropsWithChildren } from "react";
import React, { useEffect, useState } from "react";
import type { FallbackProps } from "react-error-boundary";
import { useTranslation } from "react-i18next";

import type { WindowKind } from "../../../../windowManager/WindowKind";
import { ButtonProgress } from "../../../toolbars/test/buttons/ButtonProgress";
import { useProgress } from "../../../toolbars/test/buttons/useProgress";
import { messages } from "../ErrorBoundary";
import ErrorOccurred from "../images/error-occurred.svg";

const StyledProblemOccurred = styled(ErrorOccurred)({
    width: "100%",
    height: "100%",
});

export const SectionErrorFallbackComponent = ({ children }: PropsWithChildren<Partial<FallbackProps>>) => (
    <Box
        sx={{
            width: "100%",
            height: "100%",
            overflow: "hidden",
            display: "grid",
            gridTemplateRows: "1fr auto auto 1fr",
            alignItems: "center",
            justifyContent: "center",
            justifyItems: "center",
            paddingX: 4,
        }}
    >
        <Box
            sx={{
                gridRow: "2",
                width: "100%",
                height: "100%",
                overflow: "hidden",
                position: "relative",
                display: "flex",
                alignItems: "center",
            }}
        >
            <StyledProblemOccurred sx={{ width: "100%", height: "100%", minHeight: "400px", maxWidth: "800px" }} />
        </Box>
        <Box
            sx={{
                gridRow: "3",
                display: "flex",
                flexDirection: "column",
                alignItems: "center",
                justifyContent: "center",
                textAlign: "center",
            }}
        >
            <Typography variant={"h5"}>{messages.unexpectedErrorTitle()}</Typography>
            <Typography variant={"body1"} sx={{ marginBottom: 2 }}>
                {messages.sectionUnavailable()} {messages.unexpectedErrorText()}
            </Typography>
            <Box>
                <ReloadButton />
                {children}
            </Box>
        </Box>
    </Box>
);

function CloseButton() {
    const { t } = useTranslation();
    const { close } = useWindowContext();

    const initialState = () => ({ last: Date.now(), nextIn: 15000 });
    const [refresh, setRefresh] = useState(initialState);
    const { percent = 100, isProgressing } = useProgress(refresh);

    useEffect(() => {
        if (percent > 0) return;
        close();
    }, [close, percent]);

    const reset = () => {
        setRefresh(initialState);
    };

    return (
        <Button onClick={() => close()} onMouseOver={reset} onFocus={reset} variant="text" focusRipple={false}>
            <ButtonProgress enabled={isProgressing} value={percent > 0 ? 100 - percent : 0} />
            {t("unexpectedError.closeWindowButton", "Close this window ({{seconds}})", {
                seconds: Math.max(1, Math.ceil((percent / 100) * (refresh.nextIn / 1000))),
            })}
        </Button>
    );
}

function ReloadButton() {
    const { t } = useTranslation();
    return (
        <Button onClick={() => window.location.reload()} variant="text" focusRipple={false}>
            {t("unexpectedError.reloadButton", "Reload page")}
        </Button>
    );
}

const FallbackContent = () => (
    <Box sx={{ display: "grid", overflow: "hidden", paddingBottom: 5 }}>
        <SectionErrorFallbackComponent>
            <CloseButton />
        </SectionErrorFallbackComponent>
    </Box>
);
const NullComponent = () => null;

export const DialogErrorFallbackComponent = ({ close, data: { id } }: WindowContentProps<WindowKind>) => {
    return (
        <DefaultContent
            data={{ id }}
            close={close}
            components={{
                Footer: NullComponent,
                Content: FallbackContent,
            }}
        />
    );
};
