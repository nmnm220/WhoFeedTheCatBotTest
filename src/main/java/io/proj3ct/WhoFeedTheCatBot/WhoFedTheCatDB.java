package io.proj3ct.WhoFeedTheCatBot;

import io.proj3ct.WhoFeedTheCatBot.exceptions.InvalidDobFormatException;
import io.proj3ct.WhoFeedTheCatBot.exceptions.InvalidFoodPriceFormatException;
import io.proj3ct.WhoFeedTheCatBot.objects.Food;
import io.proj3ct.WhoFeedTheCatBot.objects.Person;

import java.sql.*;
import java.util.ArrayList;

public class WhoFedTheCatDB implements WhoFedTheCat {
    private Connection conn = null;

    public WhoFedTheCatDB() {
        try {
            conn = DriverManager.getConnection("jdbc:sqlite:wftc.db");
        } catch (SQLException e) {
            System.out.println(e.getMessage());
        }
    }

    public static void main(String[] args) {
        WhoFedTheCatDB whoFedTheCatDB = new WhoFedTheCatDB();
        if (args[0].equals("setDob")) {
            int person_id = Integer.parseInt(args[1]);
            String dob = args[2];
            try {
                whoFedTheCatDB.setDob(person_id, dob);
            } catch (InvalidDobFormatException e) {
                throw new RuntimeException(e);
            }
        } else if (args[0].equals("listPeople")) {
            System.out.println(whoFedTheCatDB.listPeople());
        } else if (args[0].equals("listFood")) {
            System.out.println(whoFedTheCatDB.listFood());
        } else if (args[0].equals("deleteFood")) {
            int id = Integer.parseInt(args[1]);
            whoFedTheCatDB.deleteFood(id);
        } else if (args[0].equals("updateFood")) {
            int id = Integer.parseInt(args[1]);
            int price = Integer.parseInt(args[2]);
            try {
                whoFedTheCatDB.updateFood(id, price);
            } catch (InvalidFoodPriceFormatException e) {
                System.out.println("Invalid number");
            }
        } else if (args[0].equals("addFood")) {
            String brandName = args[1];
            int price = Integer.parseInt(args[2]);
            try {
                whoFedTheCatDB.addFood(brandName, price);
            } catch (InvalidFoodPriceFormatException e) {
                System.out.println("Invalid number");
            }
        } else if (args[0].equals("addCatFeed")) {
            int personId = Integer.parseInt(args[1]);
            int foodId = Integer.parseInt(args[2]);
            whoFedTheCatDB.addCatFeed(personId, foodId);
        }
    }

    @Override
    public void setDob(int personId, String dob) throws InvalidDobFormatException {
        Statement statement = null;
        try {
            conn.setAutoCommit(false);
            statement = conn.createStatement();
            statement.executeUpdate(String.format("insert into birthdays (dob) values (\"%s\")", dob));
            ResultSet lastId = statement.executeQuery("select last_insert_rowid();");
            lastId.next();
            int birthday_id = lastId.getInt("last_insert_rowid()");
            statement.executeUpdate(String.format("insert into people_birthdays (people_id, birthdays_id) values (" + personId + ", " +
                    birthday_id + ");"));
            conn.commit();
        } catch (SQLException e) {
            try {
                conn.rollback();
                conn.setAutoCommit(true);
                ResultSet rs = statement.executeQuery("select birthdays_id from people_birthdays where people_id=" + personId + " ;");
                rs.next();
                int birthdaysId = rs.getInt("birthdays_id");
                statement.executeUpdate("update birthdays set dob='" + dob + "' where id=" + birthdaysId + ";");
            } catch (SQLException ex) {
                throw new RuntimeException(ex);
            }
        }
    }

    @Override
    public ArrayList<Person> listPeople() {
        ArrayList<Person> people = new ArrayList<>();
        try {
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery("select id, name, telegram_id from people");
            while (rs.next()) {
                String name = rs.getString("name");
                int id = rs.getInt("id");
                String telegramId = rs.getString("telegram_id");
                Person person = new Person(id, name, telegramId);
                people.add(person);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return people;
    }

    @Override
    public ArrayList<Food> listFood() {
        ArrayList<Food> food = new ArrayList<>();
        try {
            Statement statement = conn.createStatement();
            ResultSet rs = statement.executeQuery("select id, brandname, price, display from food");
            while (rs.next()) {
                int id = rs.getInt("id");
                int price = rs.getInt("price");
                String brandName = rs.getString("brandname");
                int display = rs.getInt("display");
                Food singleFood = new Food(id, brandName, price, display == 1);
                food.add(singleFood);
            }
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
        return food;
    }

    @Override
    public void deleteFood(int id) {
        Statement statement = null;

        try {
            statement = conn.createStatement();
            statement.executeUpdate(String.format("update food set display=%s where id=%s", 0, id));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }

    @Override
    public void updateFood(int id, int price) throws InvalidFoodPriceFormatException {
        Statement statement = null;
        if (price <= 0) {
            throw new InvalidFoodPriceFormatException("Invalid number");
        }
        try {
            statement = conn.createStatement();
            statement.executeUpdate(String.format("update food set price=%s where id=%s", price, id));
        } catch (SQLException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void addFood(String brandName, int price) throws InvalidFoodPriceFormatException {
        if (price <= 0) {
            throw new InvalidFoodPriceFormatException("Invalid number");
        }
        Statement statement = null;
        try {
            statement = conn.createStatement();
            statement.executeUpdate(String.format("insert into food (brandname, price) values (\"%s\", %s)", brandName, price));
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void addCatFeed(int personId, int foodId) {
        Statement statement = null;
        try {
            statement = conn.createStatement();
            ResultSet rs = statement.executeQuery(String.format("select price from food where id=%s", foodId));
            int price = rs.getInt("price");
            statement.executeUpdate(String.format("insert into catFeeds (people_id, food_id, foodprice, date) values (%s, %s, %s, current_timestamp)", personId, foodId, price));
        } catch (SQLException e) {
        }

    }
}