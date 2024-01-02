---
sidebar_position: 7
---

# Fragments

Fragments are a way to share processing logic - if many scenarios require identically configured chains of components, fragments provide a method to avoid repetition of these chains across multiple scenarios. Fragments are a design time concept; the logic of a fragment will be executed (included into the scenario and associated Flink job) as many times as there are references to the given fragment in the deployed scenarios.

Once a fragment is created using Designer (see Fragments tab) it can be used in other scenarios in the same category.

## Inputs
Fragment must have exactly one input node. You can define parameters of a fragment:

![fragment input_base](img/fragment_input_simple.png)

Clicking the "Show advanced configuration" button opens up an additional form, that allow to set additional options for the input parameter.


For types other than `String` and `Boolean` this allows configuration as seen here:

![fragment input_advanced_long](img/fragment_input_advanced_long.png)

For parameters of type `String` or `Boolean` it also allows choosing an input mode from `Any value`, `Any value with suggestions`, `Fixed list`.

![fragment input_advanced_string](img/fragment_input_advanced_string.png)

| Field name               | Description                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
|--------------------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| Required                 | If on, the parameter's value has to be provided (the field cannot be left empty when using the fragment)      .                                                                                                                                                                                                                                                                                                                                                                                           |
| Validation               | If on, only literal values (e.g. `123`) and operations on them (e.g. `123 * 321`) are allowed as the parameter's value. This is because Nussknacker has to be able to evaluate it at deployment time.<br/>If on, allows definition of `Validation expression` and `Validation error message`.                                                                                                                                                                                                             |
| Validation expression    | SpEL expression that evaluates to a boolean result, that depends only on the parameter's value (referred to as `#value`) and literal values, e.g. `#value < 100`. <br/> When using the fragment, values that don't satisfy the expression will cause a validation error.                                                                                                                                                                                                                                  |
| Validation error message | Message that displays if the parameter's value does not satisfy `Validation expression`. If not defined, a default message, that contains the failing expression, is displayed.                                                                                                                                                                                                                                                                                                                           |
| Initial value            | The parameter's value when the fragment is first taken from the toolbox, before being changed by the user.                                                                                                                                                                                                                                                                                                                                                                                                |
| Hint text                | Message that is displayed (via popup) next to the parameter during the fragment's usage.                                                                                                                                                                                                                                                                                                                                                                                                                  |
| Input mode               | Available for parameters of type `String` or `Boolean`. The available values are:<br/>`Any value` - behaves like a standard parameter.<br/>`Any value with suggestions` - allows defining a list of values (via `Add list item`), the user can either use of the defined values or input a different one himself.<br/>`Fixed list` - allows defining a list of values (via `Add list item`), the user has to use one of the defined values (or leave the value empty, if the parameter is not `Required`) |

## Outputs
Fragment can define zero, one or more outputs. Each of them has a name (which should be unique), main scenario can then choose appropriate output. Below you can see fragment with two outputs:

![fragment output](img/fragment_output.png)

## Limitations of fragments
- They cannot access variables from the main scenario if they are not passed as parameters.
- They cannot be nested (i.e. fragment cannot invoke other fragment).
- They cannot pass output variables to the main scenario. This may change in the future.
- When inputs/outputs of fragment change, scenarios using it have to be corrected manually.
- If fragment uses some component which clears variables (e.g. aggregation with tumbling window on Flink engine), variables will be cleared also in the main scenario, even though they were not passed to fragment through fragment's input.
