import React from "react";
import { useTranslation } from "react-i18next";
import { useFieldsContext } from "../NodeRowFieldsProvider";
import { StyledButton } from "../../../focusableStyled";

export function AddButton(): JSX.Element {
    const { t } = useTranslation();
    const { add } = useFieldsContext();
    return add ? (
        <StyledButton title={t("node.row.add.title", "Add field")} onClick={add}>
            {t("node.row.add.text", "+")}
        </StyledButton>
    ) : null;
}
