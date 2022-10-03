import mapWorkspaces from "@npmcli/map-workspaces";
import concurrently from "concurrently";
import { pad } from "lodash";
import seedColor from "seed-color";
import pkg from "./package.json";

const [, , command, ...args] = process.argv;

mapWorkspaces({ pkg })
    .then((map: Map<string, string>) => [...map.keys()])
    .then((keys: string[]) => {
        const length = keys.reduce((l, k) => (k.length <= l ? l : k.length), 0);
        return keys.map((workspace) => ({
            name: pad(workspace, length),
            command: `npm run ${command} -w ${workspace} ${args.join(" ")}`,
            prefixColor: seedColor(workspace).toHex(),
        }));
    })
    .then((commands) =>
        concurrently(commands, {
            killOthers: ["failure", "success"],
            restartTries: 3,
        }),
    );
