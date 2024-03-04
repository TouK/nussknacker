import { Scenario } from "nussknackerUi/components/Process/types";
import React from "react";
import { TableCellAvatar } from "./tableCellAvatar";
import { NuIcon } from "../../common";
export function ScenarioAvatar({ scenario }: { scenario: Pick<Scenario, "isFragment" | "state"> }) {
    const { isFragment } = scenario;
    return (
        <TableCellAvatar>
            {isFragment ? (
                <NuIcon sx={{ color: "inherit" }} src={"/assets/icons/fragment.svg"} width={"1em"} height={"1em"} />
            ) : (
                <NuIcon sx={{ color: "inherit" }} src={"/assets/icons/scenario.svg"} width={"1em"} height={"1em"} />
            )}
        </TableCellAvatar>
    );
}
