package com.taskmanager.controller;

import com.taskmanager.controller.task.data.Ingredients;
import com.taskmanager.controller.task.data.OvenSettings;
import com.taskmanager.controller.task.data.SliceCake;

import static java.util.Arrays.asList;
import static java.util.Arrays.stream;



/**
 * Simple enum to encapsulate workflow steps, ordering and associated step/task data
 */
public enum MakeCakeWorkflow {

    SWITCH_OVEN_ON(new OvenSettings(210, 2, true)),
    GET_INGREDIENTS(new Ingredients(asList("250g plain flour", "125g butter", "1Tbsp baking powder", "100g sugar", "2 eggs"))),
    CAKE_MADE(new SliceCake(6));

    private Object taskData;

    MakeCakeWorkflow(Object taskData) {
        this.taskData = taskData;
    }

    public static MakeCakeWorkflow firstTask() {
        return SWITCH_OVEN_ON;
    }


    public static MakeCakeWorkflow nextTask(final MakeCakeWorkflow lastTaskPerformed){

        return stream(values()).filter(e -> e.ordinal() >  lastTaskPerformed.ordinal())
                               .findFirst()
                               .orElse(CAKE_MADE);

    }

    public Object getTaskData() {
        return taskData;
    }

}
