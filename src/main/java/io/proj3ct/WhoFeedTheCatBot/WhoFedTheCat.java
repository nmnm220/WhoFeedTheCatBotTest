package io.proj3ct.WhoFeedTheCatBot;

import io.proj3ct.WhoFeedTheCatBot.exceptions.InvalidDobFormatException;
import io.proj3ct.WhoFeedTheCatBot.exceptions.InvalidFoodPriceFormatException;
import io.proj3ct.WhoFeedTheCatBot.objects.Food;
import io.proj3ct.WhoFeedTheCatBot.objects.Person;

import java.util.ArrayList;

public interface WhoFedTheCat {
    public void setDob(int person_id, String dob) throws InvalidDobFormatException;
    public ArrayList<Person> listPeople();
    public ArrayList<Food> listFood();
    public void deleteFood(int id);
    public void updateFood(int id, int price) throws InvalidFoodPriceFormatException;
    public void addFood(String brandName, int price) throws InvalidFoodPriceFormatException;
    public void addCatFeed(int personId, int foodId);
    public String getStatsAllTime();
    public String getStatsWeek();
}
