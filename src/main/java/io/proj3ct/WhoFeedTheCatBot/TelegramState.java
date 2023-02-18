package io.proj3ct.WhoFeedTheCatBot;

import java.util.HashMap;

public class TelegramState<T> {
    private HashMap<String, T> state = new HashMap<>();
    public void setState(String id, T state)
    {
        this.state.put(id, state);
    }
    public T getState(String id) {
        return state.get(id);
    }
}
