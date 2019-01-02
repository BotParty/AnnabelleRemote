package com.lukeyes.annabelleremote;

import android.widget.Button;

public class FavoriteButton {

    public FavoriteButton(Favorite favorite) {
        this.favorite = favorite;
    }

    public Favorite getFavorite() {
        return favorite;
    }

    public Button getButton() {
        return view;
    }

    public void setButton(Button view) {
        this.view = view;
    }

    private Favorite favorite;
    private Button view;
}
