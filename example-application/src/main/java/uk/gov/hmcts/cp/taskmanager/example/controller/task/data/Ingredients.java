package uk.gov.hmcts.cp.taskmanager.example.controller.task.data;

import java.util.List;

public record Ingredients(List<String> ingredientList) {

    public List<String> getIngredientList() {
        return ingredientList;
    }
}
