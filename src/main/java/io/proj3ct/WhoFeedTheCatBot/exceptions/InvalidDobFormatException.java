package io.proj3ct.WhoFeedTheCatBot.exceptions;

public class InvalidDobFormatException extends Exception{
    public InvalidDobFormatException (String str) {
        super(str);
    }
}
