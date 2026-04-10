import { FormControlLabel, Switch } from "@mui/material";
import React from "react";
import { useTranslation } from "react-i18next";

import { useMockEnabled } from "./useMockEnabled";

interface Props {
    scenarioName: string;
    nodeId: string;
}

export const MockEnabledSwitch = ({ scenarioName, nodeId }: Props) => {
    const { t } = useTranslation();
    const [isEnabled, setEnabled] = useMockEnabled(scenarioName, nodeId);

    return (
        <FormControlLabel
            control={<Switch size="small" checked={isEnabled} onChange={(_, checked) => setEnabled(checked)} />}
            label={t("testingDialog.label.mockEnabled", "Enabled")}
            slotProps={{ typography: { variant: "body2", color: "text.secondary" } }}
            onClick={(e) => e.stopPropagation()}
        />
    );
};
