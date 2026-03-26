import AutoFixHighIcon from "@mui/icons-material/AutoFixHigh";
import React from "react";
import { useTranslation } from "react-i18next";

import { MarkdownStyled } from "./MarkdownStyled";

const Wand = () => <AutoFixHighIcon sx={{ width: "0.9em", height: "0.9em", verticalAlign: "middle" }} />;

export function ForEachAdditionalInfo() {
    const { t } = useTranslation();
    return (
        <MarkdownStyled components={{ Wand }}>
            {t(
                "node.forEachAdditionalInfo.description",
                `---\n\n**For-each** emits a separate event for each item in the **Elements** collection, making the item available downstream as the output variable.\n\nUse the <Wand /> icon to select a collection from context variables. Run a test or use live data to preview the results.`,
            )}
        </MarkdownStyled>
    );
}
