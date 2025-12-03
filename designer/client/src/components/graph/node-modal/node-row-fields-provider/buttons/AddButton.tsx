import React from "react";
import { useTranslation } from "react-i18next";

import { StyledButton } from "../../../styledButton";
import { useFieldsContext } from "../NodeRowFieldsProvider";

export function AddButton(): React.JSX.Element {
    const { t } = useTranslation();
    const { add } = useFieldsContext();
    return add ? (
        <StyledButton title={t("node.row.add.title", "Add field")} onClick={add}>
            {t("node.row.add.text", "+")}
        </StyledButton>
    ) : null;
}
