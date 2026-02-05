import AutoFixHighIcon from "@mui/icons-material/AutoFixHigh";
import ResetToDefaultIcon from "@mui/icons-material/Replay";
import { Box, Button, Menu, Typography } from "@mui/material";
import React, { useMemo, useState } from "react";
import { useTranslation } from "react-i18next";

import { SyntaxHighlighter } from "../../../../../common/SyntaxHighlighter";
import { useUserSettings } from "../../../../../common/useUserSettings";
import type { ExpressionObj } from "./types";

interface Props {
    variant: "resetToDefault" | "generate";
    defaultValue: ExpressionObj;
    handleChange: (value: ExpressionObj) => void;
}

const ResetToDefaultButton = ({ defaultValue, handleChange, variant }: Props) => {
    const { t } = useTranslation();
    const [anchorEl, setAnchorEl] = useState<null | SVGElement>(null);

    const handleIconClick = (event: React.MouseEvent<SVGElement>) => {
        setAnchorEl(event.currentTarget);
    };

    const handleClose = () => {
        setAnchorEl(null);
    };

    const handleApply = () => {
        handleChange(defaultValue);
        handleClose();
    };

    const Icon = useMemo(() => (variant === "resetToDefault" ? ResetToDefaultIcon : AutoFixHighIcon), [variant]);
    const headerTitle = useMemo(
        () =>
            variant === "resetToDefault"
                ? t("resetToDefault.header", "Reset to default value")
                : t("generateDefaultValue.header", "Default value"),
        [t, variant],
    );
    return (
        <>
            <Icon style={{ cursor: "pointer" }} fontSize="small" onClick={handleIconClick} data-testid={"resetToDefaultButton"} />
            <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={handleClose}
                anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
            >
                <Box sx={{ p: 2 }}>
                    <Typography variant="subtitle1" gutterBottom>
                        {headerTitle}
                    </Typography>
                    <SyntaxHighlighter
                        language={defaultValue.language}
                        customStyle={{ margin: 0, maxHeight: 300, maxWidth: 600, overflowY: "auto" }}
                    >
                        {defaultValue.expression}
                    </SyntaxHighlighter>
                    <Button data-testid={"applyDefaultValue"} variant="contained" size="small" onClick={handleApply} sx={{ mt: 2 }}>
                        {t("resetToDefault.button", "Apply value")}
                    </Button>
                </Box>
            </Menu>
        </>
    );
};

export function ResetToDefault({
    value,
    defaultValue,
    handleChange,
}: {
    value: string;
    defaultValue: ExpressionObj;
    handleChange: (value: ExpressionObj) => void;
}) {
    const variant = useMemo(() => (value ? "resetToDefault" : "generate"), [value]);
    const [setting] = useUserSettings("editor.showResetToDefaultButton");
    if (!setting) {
        return null;
    }

    if (!defaultValue || defaultValue.expression === value) {
        return null;
    }

    return <ResetToDefaultButton variant={variant} defaultValue={defaultValue} handleChange={handleChange} />;
}
