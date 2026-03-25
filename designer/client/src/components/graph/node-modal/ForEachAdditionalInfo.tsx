import AutoFixHighIcon from "@mui/icons-material/AutoFixHigh";
import { Box } from "@mui/material";
import React from "react";
import { Trans, useTranslation } from "react-i18next";

import { MarkdownStyled } from "./MarkdownStyled";

const wandIcon = <AutoFixHighIcon sx={{ width: "0.9em", height: "0.9em", verticalAlign: "middle" }} />;

export function ForEachAdditionalInfo() {
    const { t } = useTranslation();
    return (
        <>
            <MarkdownStyled>
                {t(
                    "node.forEachAdditionalInfo.description",
                    `---\n\n**For-each** emits a separate event for each item in the **Elements** collection, making the item available downstream as the output variable.`,
                )}
            </MarkdownStyled>
            <Box sx={(theme) => ({ ...theme.typography.body2, mb: 1.25 })}>
                <Trans
                    i18nKey="node.forEachAdditionalInfo.hint"
                    defaults="Use the <wand/> icon to select a collection from context variables. Run a test or use live data to preview the results."
                    components={{ wand: wandIcon }}
                />
            </Box>
        </>
    );
}
