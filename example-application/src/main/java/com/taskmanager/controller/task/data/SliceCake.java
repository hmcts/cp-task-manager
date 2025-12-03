package com.taskmanager.controller.task.data;

public record SliceCake(int numberOfSlices) {

    public int getNumberOfSlices() {
        return numberOfSlices;
    }
}
