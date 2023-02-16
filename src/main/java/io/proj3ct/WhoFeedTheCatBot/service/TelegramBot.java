package io.proj3ct.WhoFeedTheCatBot.service;

import io.proj3ct.WhoFeedTheCatBot.WhoFedTheCat;
import io.proj3ct.WhoFeedTheCatBot.WhoFedTheCatDB;
import io.proj3ct.WhoFeedTheCatBot.config.BotConfig;
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
    private enum State {DEFAULT, WAITING_FOR_FOOD}

    private long personId;
    private State botState = State.DEFAULT;
    private final WhoFedTheCat whoFedTheCat = new WhoFedTheCatDB();
    final BotConfig config;
    @Value("${bot.chat.id}")
    private long chatId;
    private boolean catFed = false;
    private final String catName = "Тиша";

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
        if (isHasText(update) && botState.equals(State.WAITING_FOR_FOOD)) {
            addCatFeed(update);
            sendFedMessage(update);
        } else if (isDefaultAndMessage(update, botState, "/statsalltime")) {
            sendMessageWithKeyboard(chatId, whoFedTheCat.getStatsAllTime(), false);
        } else if (isDefaultAndMessage(update, botState, "/statstoday")) {
            sendMessageWithKeyboard(chatId, whoFedTheCat.getStatsDay(), false);
        } else if (isDefaultAndMessage(update, botState, "/addCatFeed")) {
            changeStateWaitingFood(update);
        } else if (isDefaultAndMessage(update, botState, "/deleteFood")) {
            deleteFood(update);
        } else if (isItCatFeedMessage(update.getMessage().getText())) {
            sendFedMessage(update);
        }
    }

    private void addCatFeed(Update update) {
        botState = State.DEFAULT;
        ArrayList<Food> allFood = whoFedTheCat.listFood();
        int foodId = allFood.stream()
                .filter(food -> update.getMessage().getText().equals(food.brandName()))
                .toList().get(0).id();
        whoFedTheCat.addCatFeed((int) personId, foodId);

    }

    private void deleteFood(Update update) {
        String result = update.getMessage().getText().replaceAll("/deleteFood ", "");
        int id = Integer.parseInt(result);
        whoFedTheCat.deleteFood(id);
        sendMessageWithKeyboard(chatId, whoFedTheCat.listFood().toString(), false);
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
            botState = State.WAITING_FOR_FOOD;
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

    private int getTelegramIdFromDB(Update update) {
        ArrayList<Person> allPeople = whoFedTheCat.listPeople();
        return allPeople.stream()
                .filter(person -> update.getMessage().getFrom().getId() == Long.parseLong(person.telegramId()))
                .toList().get(0).id();
    }

    private ReplyKeyboardMarkup createFoodKeyboardRows() {
        List<KeyboardRow> keyboardRows = whoFedTheCat.listFood().stream().map(food -> {
            KeyboardRow row = new KeyboardRow();
            row.add(food.brandName());
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
