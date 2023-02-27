package io.proj3ct.WhoFeedTheCatBot.service;

import io.proj3ct.WhoFeedTheCatBot.TelegramState;
import io.proj3ct.WhoFeedTheCatBot.WhoFedTheCat;
import io.proj3ct.WhoFeedTheCatBot.WhoFedTheCatDB;
import io.proj3ct.WhoFeedTheCatBot.config.BotConfig;
import io.proj3ct.WhoFeedTheCatBot.exceptions.InvalidFoodPriceFormatException;
import io.proj3ct.WhoFeedTheCatBot.objects.Food;
import io.proj3ct.WhoFeedTheCatBot.objects.Person;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;


@Slf4j
@Component
public class TelegramBot extends TelegramLongPollingBot {
    private enum State {DEFAULT, WAITING_FOR_FOOD, FOOD_SETTINGS, CHANGE_PRICE, ADD_FOOD}

    private long personId;
    private final WhoFedTheCat whoFedTheCat = new WhoFedTheCatDB();
    final BotConfig config;
    @Value("${bot.chat.id}")
    private long chatId;
    private int foodIdToChange;
    private boolean catFed = false;
    private final String catName = "Тиша";

    private final TelegramState<State> botState = new TelegramState<>(State.DEFAULT);

    private final String catNameDec = catName.substring(0, catName.length() - 1) + 'у';

    public TelegramBot(BotConfig config) {
        this.config = config;
    }

    @Override
    public String getBotUsername() {
        return config.getBotName();
    }

    @Override
    public String getBotToken() {
        return System.getenv("TOKEN");
    }

    @Override
    public void onUpdateReceived(Update update) {
        State state = botState.getState(Long.toString(chatId));
        if (isHasText(update) && (state != null) && state.equals(State.WAITING_FOR_FOOD)) {
            addCatFeed(update);
            sendFedMessage(update);
        } else if (isFoodSettingsAndMessage(update, state, "Delete")) {
            deleteFood(foodIdToChange);
        } else if (isFoodSettingsAndMessage(update, state, "Change price")) {
            changeStateChangePrice(update);
            sendMessageWithKeyboard(chatId, "Enter new value", true);
        } else if (isChangePrice(update, state)) {
            changePrice(update);
        } else if (isDefaultAndMessage(update, state, "Stats for all time")) {
            sendMessageWithKeyboard(chatId, whoFedTheCat.getStatsAllTime(), false);
        } else if (isDefaultAndMessage(update, state, "Stats for today")) {
            sendMessageWithKeyboard(chatId, whoFedTheCat.getStatsDay(), false);
        } else if (isDefaultAndMessage(update, state, "Feed cat")) {
            changeStateWaitingFood(update);
        } else if (isDefaultAndMessage(update, state, "Change food")) {
            changeStateFoodSettings(update);
            createFoodKeyboardRows();
        } else if (isDefaultAndMessage(update, state, "Add food")) {
            changeStateFoodSettings(update);
            createFoodKeyboardRows();
        } else if (isFoodSettings(update, state)) {
            showFoodChangeKeyboard(update);
        } else if (isItCatFeedMessage(update.getMessage().getText())) {
            sendFedMessage(update);
        }
    }

    private void addCatFeed(Update update) {
        botState.setState(Long.toString(chatId), State.DEFAULT);
        ArrayList<Food> allFood = whoFedTheCat.listFood();
        int foodId = allFood.stream()
                .filter(food -> update.getMessage().getText().contains(food.brandName()))
                .toList().get(0).id();
        System.out.println("Food id " + foodId);
        whoFedTheCat.addCatFeed((int) personId, foodId);

    }

    private void deleteFood(int foodIdToChange) {
        whoFedTheCat.deleteFood(foodIdToChange);
        sendMessageWithKeyboard(chatId, "Food deleted", false);
        botState.setState(Long.toString(chatId), State.DEFAULT);
    }

    private void changePrice(Update update) {
        System.out.println(botState.getState(Long.toString(chatId)));
        int newPrice = Integer.parseInt(update.getMessage().getText());
        try {
            whoFedTheCat.updateFood(foodIdToChange, newPrice);
        } catch (InvalidFoodPriceFormatException e) {
            throw new RuntimeException(e);
        }
        sendMessageWithKeyboard(chatId, "Price changed", false);
        botState.setState(Long.toString(chatId), State.DEFAULT);
    }

    private void sendFedMessage(Update update) {
        long chatId = update.getMessage().getChatId();
        if (!catFed) {
            SimpleDateFormat simpleDateFormat = new SimpleDateFormat("k:mm");
            Date currDate = new Date();
            sendMessageWithKeyboard(chatId, update.getMessage().getFrom().getFirstName() + " покормил(а) меня в " + simpleDateFormat.format(currDate) + "." +
                    "\nУра! Теперь я сыт.", true);
            catFed = true;
            this.chatId = update.getMessage().getChatId();
        } else {
            sendMessageWithKeyboard(chatId, "Меня уже покормили раньше.", true);
        }
    }

    private void changeStateWaitingFood(Update update) {
        personId = getTelegramIdFromDB(update);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Food");
        message.setReplyMarkup(createFoodKeyboardRows());
        try {
            execute(message);
            botState.setState(Long.toString(chatId), State.WAITING_FOR_FOOD);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void showFoodChangeKeyboard(Update update) {
        ArrayList<Food> allFood = whoFedTheCat.listFood();
        foodIdToChange = allFood.stream()
                .filter(food -> update.getMessage().getText().contains(food.brandName()))
                .toList().get(0).id();
        System.out.println(foodIdToChange);
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Change price");
        row.add("Delete");
        keyboardRows.add(row);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setKeyboard(keyboardRows);
        message.setReplyMarkup(keyboardMarkup);
        message.setText("Change price/delete");
        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void changeStateFoodSettings(Update update) {
        botState.setState(Long.toString(chatId), State.FOOD_SETTINGS);
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText("Food settings");
        message.setReplyMarkup(createFoodKeyboardRows());
        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }

    private void changeStateChangePrice(Update update) {
        sendMessageWithKeyboard(chatId, "Enter new value", true);
        botState.setState(Long.toString(chatId), State.CHANGE_PRICE);
        SendMessage message = new SendMessage();
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        message.setReplyMarkup(replyKeyboardMarkup);
        try {
            execute(message);
        } catch (TelegramApiException e) {
            throw new RuntimeException(e);
        }
    }


    private void sendMessageWithKeyboard(long chatId, String textToSend, boolean hideKeyboard) {
        SendMessage message = new SendMessage();
        message.setChatId(chatId);
        message.setText(textToSend);
        List<KeyboardRow> keyboardRows = new ArrayList<>();
        KeyboardRow row = new KeyboardRow();
        row.add("Stats for all time");
        row.add("Stats for today");
        row.add("Feed cat");
        row.add("Change food");
        row.add("Add food");
        keyboardRows.add(row);
        if (hideKeyboard) {
            ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
            keyboardMarkup.setResizeKeyboard(true);
            keyboardMarkup.setKeyboard(keyboardRows);
            message.setReplyMarkup(keyboardMarkup);
        }
        try {
            execute(message);
        } catch (TelegramApiException e) {
            log.error("Error occurred: " + e.getMessage());
        }
    }

    private boolean isItCatFeedMessage(String message) {
        if (message != null) {
            message = message.toLowerCase();
            return message.contains("я покормил");
        }
        return false;
    }

    @Scheduled(cron = "0 0 5,14,22 * * *")
    private void feedReminder() {
        catFed = false;
        if (chatId != 0) {
            String messageToSend = "Я хочу кушать, покормите меня!";
            sendMessageWithKeyboard(chatId, messageToSend, false);
        }
    }

    private boolean isHasText(Update update) {
        return update.hasMessage() && update.getMessage().hasText();
    }

    private boolean isDefaultAndMessage(Update update, State state, String botCommand) {
        return isHasText(update) && state.equals(State.DEFAULT) && update.getMessage().getText().equals(botCommand);
    }

    private boolean isFoodSettings(Update update, State state) {
        return isHasText(update) && state.equals(State.FOOD_SETTINGS);
    }

    private boolean isFoodSettingsAndMessage(Update update, State state, String botCommand) {
        return isHasText(update) && state.equals(State.FOOD_SETTINGS) && update.getMessage().getText().equals(botCommand);
    }

    private boolean isChangePrice(Update update, State state) {
        return isHasText(update) && state.equals(State.CHANGE_PRICE);
    }

    private int getTelegramIdFromDB(Update update) {
        ArrayList<Person> allPeople = whoFedTheCat.listPeople();
        return allPeople.stream()
                .filter(person -> update.getMessage().getFrom().getId() == Long.parseLong(person.telegramId()))
                .toList().get(0).id();
    }

    private ReplyKeyboardMarkup createFoodKeyboardRows() {
        List<KeyboardRow> keyboardRows = whoFedTheCat.listFood().stream().map(food -> {
            KeyboardRow row = new KeyboardRow();
            row.add(food.brandName() + " (" + food.price() + "₽)");
            return row;
        }).toList();
        ReplyKeyboardMarkup keyboardMarkup = new ReplyKeyboardMarkup();
        keyboardMarkup.setResizeKeyboard(true);
        keyboardMarkup.setKeyboard(keyboardRows);
        return keyboardMarkup;
    }

    /*@Scheduled(cron = "0 30 * * * *")
    private void checkCatFed() {
        if (!catFed) {
            String messageToSend = EmojiParser.parseToUnicode("Похоже " + catNameDec + " никто не покормил " + ":pleading_face:");
            sendMessage(chatId ,messageToSend);
        }
    }*/
}
