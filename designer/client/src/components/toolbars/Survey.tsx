import { Button, Stack, Typography, useTheme } from "@mui/material";
import React, { useCallback } from "react";
import { useTranslation } from "react-i18next";
import { useWindows, WindowKind } from "../../windowManager";
import { ToolbarPanelProps } from "../toolbarComponents/DefaultToolbarPanel";
import { ToolbarWrapper } from "../toolbarComponents/toolbarWrapper/ToolbarWrapper";
import { useSurvey } from "./useSurvey";

function Survey(props: ToolbarPanelProps) {
    const { t } = useTranslation();
    const theme = useTheme();
    const [survey, hideSurvey] = useSurvey();

    const { open } = useWindows();
    const onOpen = useCallback(
        () =>
            survey &&
            open({
                kind: WindowKind.survey,
                meta: survey.link,
                isResizable: true,
                shouldCloseOnEsc: false,
                width: 750,
                height: 900,
            }),
        [open, survey],
    );

    if (!survey) {
        return null;
    }

    return (
        <ToolbarWrapper {...props} onClose={hideSurvey} color={theme.palette.success.dark}>
            <Stack p={1} spacing={0.5}>
                <Typography variant="body2">{survey.text}</Typography>
                <Stack direction="row" spacing={1}>
                    <Button size="small" variant="text" onClick={onOpen}>
                        {t("panels.survey.ok", "let's go!")}
                    </Button>
                    <Button size="small" variant="text" onClick={hideSurvey}>
                        {t("panels.survey.no", "close")}
                    </Button>
                </Stack>
            </Stack>
        </ToolbarWrapper>
    );
}

export default Survey;
