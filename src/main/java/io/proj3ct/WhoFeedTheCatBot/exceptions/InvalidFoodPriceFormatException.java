package io.proj3ct.WhoFeedTheCatBot.exceptions;

public class InvalidFoodPriceFormatException extends Exception{
    public InvalidFoodPriceFormatException(String str) {
        super(str);
    }
}
