import ResetToDefaultIcon from "@mui/icons-material/Replay";
import { Menu, Button, Typography, Box } from "@mui/material";
import React, { useState } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";

import { SyntaxHighlighter } from "../../../../../common/SyntaxHighlighter";
import { getUserSettings } from "../../../../../reducers/selectors/userSettings";

interface Props {
    defaultValue: string;
    handleChange: (expression: string) => void;
}

export const ResetToDefaultButton = ({ defaultValue, handleChange }: Props) => {
    const userSettings = useSelector(getUserSettings);
    const showResetToDefaultButton = userSettings["editor.showResetToDefaultButton"];

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

    if (!showResetToDefaultButton) {
        return;
    }

    return (
        <>
            <ResetToDefaultIcon style={{ cursor: "pointer" }} fontSize="small" onClick={handleIconClick} />
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
                    <SyntaxHighlighter language="json" customStyle={{ margin: 0, maxHeight: 300, maxWidth: 600, overflowY: "auto" }}>
                        {defaultValue}
                    </SyntaxHighlighter>
                    <Button variant="contained" size="small" onClick={handleApply} sx={{ mt: 2 }}>
                        {t("resetToDefault.button", "Apply Default Values")}
                    </Button>
                </Box>
            </Menu>
        </>
    );
};
