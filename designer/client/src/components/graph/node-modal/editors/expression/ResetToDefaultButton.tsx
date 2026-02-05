import ResetToDefaultIcon from "@mui/icons-material/Replay";
import { Box, Button, Menu, Typography } from "@mui/material";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";

import { SyntaxHighlighter } from "../../../../../common/SyntaxHighlighter";
import { useUserSettings } from "../../../../../common/useUserSettings";
import type { ExpressionObj } from "./types";

interface Props {
    defaultValue: ExpressionObj;
    handleChange: (value: ExpressionObj) => void;
}

const ResetToDefaultButton = ({ defaultValue, handleChange }: Props) => {
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

    return (
        <>
            <ResetToDefaultIcon
                style={{ cursor: "pointer" }}
                fontSize="small"
                onClick={handleIconClick}
                data-testid={"resetToDefaultButton"}
            />
            <Menu
                anchorEl={anchorEl}
                open={Boolean(anchorEl)}
                onClose={handleClose}
                anchorOrigin={{ vertical: "bottom", horizontal: "left" }}
            >
                <Box sx={{ p: 2 }}>
                    <Typography variant="subtitle1" gutterBottom>
                        {t("resetToDefault.header", "Reset to default value")}
                    </Typography>
                    <SyntaxHighlighter
                        language={defaultValue.language}
                        customStyle={{ margin: 0, maxHeight: 300, maxWidth: 600, overflowY: "auto" }}
                    >
                        {defaultValue.expression}
                    </SyntaxHighlighter>
                    <Button variant="contained" size="small" onClick={handleApply} sx={{ mt: 2 }}>
                        {t("resetToDefault.button", "Apply Default Values")}
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
    const [setting] = useUserSettings("editor.showResetToDefaultButton");
    if (!setting) {
        return null;
    }

    if (!defaultValue || defaultValue.expression === value) {
        return null;
    }

    return <ResetToDefaultButton defaultValue={defaultValue} handleChange={handleChange} />;
}
