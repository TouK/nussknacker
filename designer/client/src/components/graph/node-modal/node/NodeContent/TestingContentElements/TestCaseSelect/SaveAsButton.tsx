import React from "react";
import { useTranslation } from "react-i18next";

import { StyledButton } from "../../../../../styledButton";
import { InfoTooltip } from "../../../../editors/InfoTooltip/InfoTooltip";

type Props = {
    onClick: () => void;
};

export const SaveAsButton = ({ onClick }: Props) => {
    const { t } = useTranslation();

    return (
        <InfoTooltip title={"Save as"} variant={"hover"} enterDelay={500}>
            <StyledButton data-testid="save-as-test-case" title={t("node.row.add.title", "Add test case")} onClick={onClick}>
                {t("node.row.add.text", "+")}
            </StyledButton>
        </InfoTooltip>
    );
};
