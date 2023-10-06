import React, { useMemo } from "react";
import { ProcessLink } from "../../containers/processLink";
import ProcessBackIcon from "../../assets/img/arrows/back-process.svg";
import { useTranslation } from "react-i18next";
import { matchPath, useLocation } from "react-router-dom";
import { MetricsBasePath } from "../../containers/paths";
import { styled } from "@mui/material";
import { variables } from "../../stylesheets/variables";

const BackIcon = styled(ProcessBackIcon)(() => ({
    height: "12px",
}));

const ButtonText = styled("span")(({ theme }) => ({
    fontSize: "14px",
    fontWeight: 600,
    color: theme.custom.colors.secondaryColor,
    marginLeft: "8px",
}));

const ProcessLinkButton = styled(ProcessLink)(() => ({
    backgroundColor: variables.buttonBorderColor,
    border: `1px solid ${variables.menuButtonBorderColor}`,
    borderRadius: "3px",
    height: "25px",
    display: "flex",
    alignItems: "center",
    padding: "0 8px",
    cursor: "pointer",
    "&:hover, &:focus": {
        backgroundColor: variables.menuButtonActiveBKColor,
    },
}));

export default function ProcessBackButton() {
    const { t } = useTranslation();
    const { pathname } = useLocation();
    const processId = useMemo(() => {
        const match = matchPath(`${MetricsBasePath}/:processId`, pathname);
        return match?.params?.processId;
    }, [pathname]);

    if (!processId) {
        return null;
    }

    return (
        <ProcessLinkButton processId={processId} title={t("processBackButton.title", "Go back to {{processId}} graph page", { processId })}>
            <BackIcon />
            <ButtonText>{t("processBackButton.text", "back to {{processId}}", { processId })}</ButtonText>
        </ProcessLinkButton>
    );
}
