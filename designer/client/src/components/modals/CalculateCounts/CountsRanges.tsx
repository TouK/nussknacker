import type { Moment } from "moment";
import React, { useMemo } from "react";
import { useTranslation } from "react-i18next";

import { getProcessingType, getProcessName } from "../../../reducers/selectors/graph";
import { useAppSelector } from "../../../store/storeHelpers";
import { CountsRangesButtons } from "./CountsRangesButtons";
import { StyledRangesWrapper } from "./CountsStyled";
import { useActivityHistory } from "./useActivityHistory";
import { predefinedRanges } from "./utils";

interface RangesProps {
    label: string;
    onChange: (value: [Moment, Moment], refresh?: number | null) => void;
}

export function CountsRanges({ label, onChange }: RangesProps): React.JSX.Element {
    const { t } = useTranslation<string>();
    const processName = useAppSelector(getProcessName);
    const processCategory = useAppSelector(getProcessingType);
    const activities = useActivityHistory(processName, processCategory);
    const dates = useMemo(() => predefinedRanges(t), [t]);

    return (
        <>
            <p>{label}</p>
            <StyledRangesWrapper data-autofocus>
                <CountsRangesButtons ranges={dates} onChange={onChange} />
                <CountsRangesButtons ranges={activities} onChange={onChange} limit={1}>
                    {t("calculateCounts.activities", "Previous activities...")}
                </CountsRangesButtons>
            </StyledRangesWrapper>
        </>
    );
}
