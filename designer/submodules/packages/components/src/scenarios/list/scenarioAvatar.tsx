import { Scenario } from "nussknackerUi/components/Process/types";
import React from "react";
import { TableCellAvatar } from "./tableCellAvatar";
import ScanarioIcon from "../../assets/icons/scenario.svg";
import FragmentIcon from "../../assets/icons/fragment.svg";
export function ScenarioAvatar({ scenario }: { scenario: Pick<Scenario, "isFragment" | "state"> }) {
    const { isFragment } = scenario;
    return (
        <TableCellAvatar>
            {isFragment ? <FragmentIcon width={"1em"} height={"1em"} /> : <ScanarioIcon width={"1em"} height={"1em"} />}
        </TableCellAvatar>
    );
}
