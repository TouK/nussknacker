import React from "react";
import { useTranslation } from "react-i18next";
import { StyledButtonWithFocus } from "../../../focusableStyled";

export function RemoveButton({ onClick }: { onClick: () => void }): JSX.Element {
    const { t } = useTranslation();
    return (
        <StyledButtonWithFocus title={t("node.row.remove.title", "Remove field")} onClick={onClick}>
            {t("node.row.remove.text", "-")}
        </StyledButtonWithFocus>
    );
}
