import CheckCircleIcon from "@mui/icons-material/CheckCircle";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";
import { Box, IconButton } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { InfoTooltip } from "../../../editors/InfoTooltip/InfoTooltip";

type Props = {
    status: "success" | "error";
    message: string | undefined;
};

export const AssertionStatus: React.FC<Props> = ({ status, message }) => {
    const { t } = useTranslation();
    const isSuccess = status === "success";
    const Icon = isSuccess ? CheckCircleIcon : ErrorOutlineIcon;
    const color = isSuccess ? "success.main" : "error.main";

    return (
        <Box component="span" sx={{ display: "inline-flex", alignItems: "center" }}>
            <InfoTooltip title={status === "error" ? message : t("testingDialog.assertions.status.ok", "ok")} variant={"hover"}>
                <IconButton
                    size="small"
                    aria-label={isSuccess ? "success" : "error"}
                    sx={{
                        p: 0.3,
                        color,
                        "&:hover": { background: "transparent" },
                    }}
                >
                    <Icon fontSize="small" />
                </IconButton>
            </InfoTooltip>
        </Box>
    );
};
