package com.taskmanager.controller.task.data;

public record OvenSettings(int degreesCelsius, int shelfNumber, boolean useSteamFunction) {

    public int getDegreesCelsius() {
        return degreesCelsius;
    }

    public int getShelfNumber() {
        return shelfNumber;
    }

    public boolean isUseSteamFunction() {
        return useSteamFunction;
    }
}
