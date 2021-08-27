---
sidebar_position: 6
---

# Fragments

Fragments are a way to share common logic. 

Fragment first has to be designed (you can access fragments via Fragments tab) and then it can be used in other scenarios in same category

## Inputs
Fragment can have one input. You can define parameters of a fragment:

![fragment input](img/fragment_input.png)

Currently, they have to be given as fully qualified Java type e.g. `java.lang.String`, `java.lang.Long`

## Outputs
Fragment can define zero, one or more outputs. Each of them has a name, main scenario can then choose appropriate output. Below you can see fragment with two outputs:

![fragment output](img/fragment_output.png)

## Limitations of fragments
- They cannot access variables from the main scenario if they are not passed as parameters.
- They cannot be nested (i.e. fragment cannot invoke other fragment).
- They cannot pass output variables to the main scenario. This may change in the future.
- When inputs/outputs of fragment change, scenarios using it have to be corrected manually.
