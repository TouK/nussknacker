import { createSelector } from "reselect";

import type { Layout } from "../../actions/nk";
import type { RootState } from "../index";
import { getGraph } from "./graph";

export const getLayout: (state: RootState) => Layout = createSelector(getGraph, (g) => g.layout);
