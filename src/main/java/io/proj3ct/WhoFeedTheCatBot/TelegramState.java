package io.proj3ct.WhoFeedTheCatBot;

import java.util.HashMap;

public class TelegramState<T> {
    private final HashMap<String, T> state = new HashMap<>();
    private T defaultState;
    public TelegramState(T defaultState) {
        this.defaultState = defaultState;
    }

    public void setState(String id, T state)
    {
        this.state.put(id, state);
    }
    public T getState(String id) {
        if (state.get(id) == null) {
            setState(id, defaultState);
        }
        return state.get(id);
    }
}
