import chalk from "chalk";
import { spawn } from "child_process";
import { debounce } from "lodash";
import { Compiler } from "webpack";

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
                const [command, ...args] = script.split(" ");
                const process = spawn(command, args, { shell: true });
                if (verbose) {
                    process.stdout.on("data", (data) => this.log(data.toString()));
                }
                process.stderr.on("data", (error) => this.error(error.toString()));
                process.on("close", (code) => resolve(code));
                process.on("error", (err) => reject(err));
            });
        }
        this.log(chalk.green("All done."));
    }, 10000);

    constructor(options: Options | Options["scripts"]) {
        const defaultOption = { scripts: [], verbose: true };
        this.options = Array.isArray(options) ? { ...defaultOption, scripts: options } : { ...defaultOption, ...options };
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
