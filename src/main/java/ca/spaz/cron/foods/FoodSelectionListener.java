package ca.spaz.cron.foods;

import ca.spaz.cron.datasource.FoodProxy;

public interface FoodSelectionListener {
    public void foodSelected(FoodProxy food);
    public void foodDoubleClicked(FoodProxy food);
}
