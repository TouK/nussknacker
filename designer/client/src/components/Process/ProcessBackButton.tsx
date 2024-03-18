import React, { useMemo } from "react";
import { ProcessLink } from "../../containers/processLink";
import ProcessBackIcon from "../../assets/img/arrows/back-process.svg";
import { useTranslation } from "react-i18next";
import { matchPath, useLocation } from "react-router-dom";
import { MetricsBasePath } from "../../containers/paths";
import { styled, Typography } from "@mui/material";
import { blendLighten } from "../../containers/theme/nuTheme";

const BackIcon = styled(ProcessBackIcon)(() => ({
    height: "12px",
}));

const ProcessLinkButton = styled(ProcessLink)(({ theme }) => ({
    color: theme.palette.text.primary,
    backgroundColor: blendLighten(theme.palette.background.paper, 0.2),
    display: "flex",
    alignItems: "center",
    padding: theme.spacing(0, 1),
    cursor: "pointer",
    "&:hover, &:focus": {
        textDecoration: "none",
        backgroundColor: theme.palette.action.hover,
    },
}));

export default function ProcessBackButton() {
    const { t } = useTranslation();
    const { pathname } = useLocation();
    const processName = useMemo(() => {
        const match = matchPath(`${MetricsBasePath}/:processName`, pathname);
        return match?.params?.processName;
    }, [pathname]);

    if (!processName) {
        return null;
    }

    return (
        <ProcessLinkButton
            processName={processName}
            title={t("processBackButton.title", "Go back to {{processName}} graph page", { processName })}
        >
            <BackIcon />
            <Typography ml={1}>{t("processBackButton.text", "Back to {{processName}}", { processName })}</Typography>
        </ProcessLinkButton>
    );
}
