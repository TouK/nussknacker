import { Moment } from "moment";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";
import { useSelector } from "react-redux";
import { getProcessName } from "../../../reducers/selectors/graph";
import { CountsRangesButtons } from "./CountsRangesButtons";
import { useDeployHistory } from "./useDeployHistory";
import { predefinedRanges } from "./utils";
import { StyledRangesWrapper } from "./CountsStyled";
import { useActivityHistory } from "./useActivityHistory";

interface RangesProps {
    label: string;
    onChange: (value: [Moment, Moment], refresh?: number | null) => void;
}

export function CountsRanges({ label, onChange }: RangesProps): JSX.Element {
    const { t } = useTranslation<string>();
    const processName = useSelector(getProcessName);
    const activities = useActivityHistory(processName);
    const dates = useMemo(() => predefinedRanges(t), [t]);

    return (
        <>
            <p>{label}</p>
            <StyledRangesWrapper>
                <CountsRangesButtons ranges={dates} onChange={onChange} />
                <CountsRangesButtons ranges={activities} onChange={onChange} limit={1}>
                    {t("calculateCounts.activities", "Previous activities...")}
                </CountsRangesButtons>
            </StyledRangesWrapper>
        </>
    );
}
