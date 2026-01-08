package uk.gov.hmcts.cp.taskmanager.example.controller.task.data;

public record SliceCake(int numberOfSlices) {

    public int getNumberOfSlices() {
        return numberOfSlices;
    }
}
