/*
 * Created on Apr 16, 2005 by davidson
 */
package ca.spaz.cron.foods;

/**
 * A base class for a group of related nutrient values Provides common support
 * for loading and updating the database.
 * 
 * The nutrient table is expected to have field names that match the names and
 * types of the corresponding database table. This greatly simplifies
 * maintaining the database and generating UI forms through reflection.
 * 
 * @author davidson
 */
public class NutrientTable {

   public double[] nutrients = new double[NutrientInfo.getGlobalList().size()];
   
   public NutrientTable() {}

   public NutrientTable(NutrientTable nt) {
      System.arraycopy(nt.nutrients, 0, nutrients, 0, nutrients.length);
   }

   public double getAmount(int index) {
      return nutrients[index];
   }

   public void setAmount(int index, double val) {
      nutrients[index] = val;
   }

   /**
    * Add the nutrients in the given table to our total
    * 
    * @param toAdd
    *            the nutrients ratios to add
    * @param weight
    *            multiplier for the amount in the added nutrients
    */
   public void addFood(NutrientTable toAdd, double weight) {
      for (int i = 0; i < nutrients.length; i++) {
         nutrients[i] += toAdd.getAmount(i) * weight;
      }
   }

   public void addFood(Serving food) {
      addFood(food.getFood().getNutrients(), food.getGrams() / 100.0);
   }

}
