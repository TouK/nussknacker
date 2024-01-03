/* eslint-disable i18next/no-literal-string */
import Moment from "moment";
import * as queryString from "query-string";
import { NodeId } from "../types";
import { VisualizationBasePath } from "../containers/paths";

function fromTimestampOrDate(tsOrDate) {
    const asInt = parseInt(tsOrDate);

    if (Number.isInteger(asInt) && !isNaN(tsOrDate)) {
        return Moment(asInt);
    }

    return Moment(tsOrDate);
}

export function visualizationUrl(processName: string, nodeId?: NodeId): string {
    const baseUrl = `${VisualizationBasePath}/${encodeURIComponent(processName)}`;
    return queryString.stringifyUrl({ url: baseUrl, query: { nodeId } });
}

export function extractCountParams(queryParams) {
    if (queryParams.from || queryParams.to) {
        const from = queryParams.from ? fromTimestampOrDate(queryParams.from) : null;
        const to = queryParams.to ? fromTimestampOrDate(queryParams.to) : Moment();
        return { from, to };
    }

    return null;
}
