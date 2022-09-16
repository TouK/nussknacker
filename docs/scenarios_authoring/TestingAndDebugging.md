---
sidebar_position: 8
---

# Testing and Debugging

There are several features in Nussknacker designed specifically to ease testing and debugging; this page provides a brief explanation how to use them. This functionality is grouped in the Test panel docked in  the right side of the Designer canvas; if you did not rearrange panels it is in the right part of the Designer canvas. 

![alt_text](img/testPanel.png "Designer Test panel")


## Test data capture

You can use `generate` button to capture events from the input Kafka topic into the file. Yo will be prompted to enter number of events to be captured. This feature works only if there is one Kafka input topic to the scenario.

## Testing using events from file

A scenario can be tested with events coming from a file; this can be very handy if several test passes on the same input events are needed before the scenario is deemed ready. Similarly as with test data capture, this feature works only if there is one Kafka input topic to the scenario.

If you want to read more than 20 records from file, you will need to change [testing settings](/docs/installation_configuration_guide/DesignerConfiguration#testing).


## Debugging node behaviour 

Almost all nodes use SpEL expressions; sometimes it is not clear what was the result of the SpEL expression for the given input record. If scenario is run using data from file it is possible to check results of expression evaluation - separately for each input record. 

![alt_text](img/nodeDebugging.png "Debugging a node") 

You can also watch [this video](/quickstart/docker#correcting-errors) to see debugging functionality in action.


## Watching nodes filtering behaviour with counts

Understanding how many events passed through a given node can be very handy during debugging - choose `counts` button to see the counts snapshoot. The number displayed is the number of events which entered a given node. 

In some edge cases, you may need to change algorithm used for counts computations - consult the [counts configuration](/docs/installation_configuration_guide/DesignerConfiguration#counts) for details.

![alt_text](img/Counts.png "Watching nodes filtering behaviour")