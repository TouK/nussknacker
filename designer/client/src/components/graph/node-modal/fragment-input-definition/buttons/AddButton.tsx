import React from "react";
import { useTranslation } from "react-i18next";
import { useFieldsContext } from "../NodeRowFields";
import { StyledButtonWithFocus } from "../../../focusableStyled";

export function AddButton(): JSX.Element {
    const { t } = useTranslation();
    const { add } = useFieldsContext();
    return add ? (
        <StyledButtonWithFocus title={t("node.row.add.title", "Add field")} onClick={add}>
            {t("node.row.add.text", "+")}
        </StyledButtonWithFocus>
    ) : null;
}
