package pl.touk.nussknacker.engine.api.validation;

public enum ValidatorMode {
    // Derive the stage from the parameter kind: eager -> compile time only, lazy -> compile time and runtime.
    AUTO,
    // Always run only at compile time, on the compile-time-evaluated value.
    COMPILE_TIME,
    // Always run at both compile time and runtime.
    COMPILE_TIME_AND_RUNTIME
}
