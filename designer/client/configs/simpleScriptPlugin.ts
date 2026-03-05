import { spawn } from "child_process";
import { platform } from "process";

import chalk from "chalk";
import { debounce } from "lodash";
import type { Compiler } from "webpack";

interface Options {
    scripts: Array<string>;
    verbose?: boolean;
    quiet?: boolean;
}

export class SimpleScriptPlugin {
    private readonly options: Options;
    private runScripts = debounce(async () => {
        const { scripts, verbose } = this.options;
        this.log(chalk.cyan("Emit tap..."));
        for (const script of scripts) {
            this.log(chalk.blue(script));
            await new Promise((resolve, reject) => {
                const shell = platform === "win32" && !!process.env.MSYSTEM ? process.env.SHELL : true;
                const child = spawn(script, { shell });
                if (verbose) {
                    child.stdout.on("data", (data) => this.log(data.toString()));
                }
                child.stderr.on("data", (error) => this.error(error.toString()));
                child.on("close", (code) => resolve(code));
                child.on("error", (err) => reject(err));
            });
        }
        this.log(chalk.green("All done."));
    }, 10000);

    constructor(options: Options | Options["scripts"]) {
        const defaultOptions = { scripts: [], verbose: true };
        this.options = Array.isArray(options) ? { ...defaultOptions, scripts: options } : { ...defaultOptions, ...options };
    }

    apply(compiler: Compiler): void {
        compiler.hooks.emit.tap(SimpleScriptPlugin.name, this.runScripts);
    }

    private log(message: string): void {
        if (!this.options.quiet) {
            message
                .trim()
                .split("\n")
                .forEach((line) => console.log(chalk.blue.dim(`[${SimpleScriptPlugin.name}]`), line));
        }
    }

    private error(message: string): void {
        if (!this.options.quiet) {
            message
                .trim()
                .split("\n")
                .forEach((line) => console.error(chalk.red.dim(`[${SimpleScriptPlugin.name}]`), chalk.red(line)));
        }
    }
}
