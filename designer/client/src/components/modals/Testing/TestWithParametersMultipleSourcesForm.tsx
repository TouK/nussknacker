import { Box, Typography } from "@mui/material";
import React, { useEffect } from "react";
import { useTranslation } from "react-i18next";

import { useTestingContext } from "./TestingContext";

interface TestWithParametersMultipleSourcesFormProps {
    numberOfSources: number;
}

export function TestWithParametersMultipleSourcesForm({ numberOfSources }: TestWithParametersMultipleSourcesFormProps): JSX.Element {
    const { t } = useTranslation();
    const { handleSetAction, handleIsValid } = useTestingContext();

    useEffect(() => {
        handleIsValid(false);
        handleSetAction(() => undefined);
    }, [handleIsValid, handleSetAction]);

    return (
        <Box
            sx={{ display: "flex", flexWrap: "wrap", justifyContent: "center", gap: 2, width: "100%" }}
            style={{ marginBottom: "12px", marginTop: "12px" }}
        >
            <Typography component="span" variant={"subtitle1"} noWrap={false} align={"center"}>
                {t("panels.actions.scenarioTest.testWithFormForMultipleSourcesError", {
                    count: numberOfSources,
                    defaultValue: "Test with form is supported only for scenario with single source. Your scenario has {{count}} sources.",
                })}
            </Typography>
        </Box>
    );
}
