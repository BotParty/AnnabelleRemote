package com.lukeyes.annabelleremote;

import android.content.Context;
import android.content.res.AssetManager;
import android.content.res.Resources;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public Button connectButton;

    public Button disconnectButton;

    public Button sendButton;

    public TextView addressText;
    public TextView editText;

    Context context;

    ObjectMapper objectMapper;
    final private String MY_ID = "remote";
    private static final String AUTO_ADDRESS = "192.168.8.127";

    WebSocketClient webSocketClient = null;

    List<FavoriteButton> favoriteButtons;

    public MainActivity() {
        favoriteButtons = new ArrayList<>();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        objectMapper = new ObjectMapper();

        setContentView(R.layout.activity_main);
        this.context = this;
        load();

        connectButton = (Button) findViewById(R.id.connect_button);
        connectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onConnect();
            }
        });
        connectButton.setEnabled(true);

        // Upon interacting with UI controls, delay any scheduled hide()
        // operations to prevent the jarring behavior of controls going away
        // while interacting with the UI.
        disconnectButton = (Button) findViewById(R.id.disconnect_button);
        disconnectButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onDisconnect();
            }
        });
        disconnectButton.setEnabled(false);

        sendButton = (Button) findViewById(R.id.send_button);
        sendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendCustomText();
            }
        });

        editText = (TextView) findViewById(R.id.edit_text);

        final Resources resources = getResources();

        for(int i = 0; i < favoriteButtons.size(); i++) {
            final FavoriteButton favoriteButton = favoriteButtons.get(i);
            int buttonId = resources.getIdentifier(String.format("button_%d", i), "id", this.getPackageName());
            final View view = findViewById(buttonId);
            if(view != null) {
                final Favorite favorite = favoriteButton.getFavorite();
                final Button button = (Button) view;
                button.setText(favorite.getLabel());
                favoriteButton.setButton(button);
                button.setOnClickListener(new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        final String text = favorite.getText();
                        sendText(text);                    }
                });
            }
        }

        addressText = (TextView) findViewById(R.id.address_text);
        addressText.setText(AUTO_ADDRESS);
    }

    private void sendCustomText() {
        if(editText == null)
            return;

        final CharSequence charSequence = editText.getText();
        if(charSequence == null || charSequence.length() == 0)
            return;

        final String textToSend = String.valueOf(charSequence);
        sendText(textToSend);
    }

    public void sendText(String text) {

        Message message = new Message();
        message.sender = MY_ID;
        message.recipient = "face";
        message.message = text;
        try {
            String wrappedMessage = objectMapper.writeValueAsString(message);
            if(webSocketClient != null)
                webSocketClient.send(wrappedMessage);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
    }

    public void onConnect() {
        final String address = String.valueOf(addressText.getText());
        connectWebSocket(address);
    }

    public void onDisconnect() {
        Toast.makeText(this, "Disconnect", Toast.LENGTH_SHORT).show();
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                connectButton.setEnabled(true);
                disconnectButton.setEnabled(false);
                addressText.setEnabled(true);
            }
        });
    }

    private void connectWebSocket(String address) {
        URI uri;
        try {
            String socketAddress = String.format("ws://%s:8080", address);
            String toastText = String.format("Connecting to %s", socketAddress);
            Toast.makeText(this,toastText,Toast.LENGTH_SHORT).show();
            uri = new URI(socketAddress);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        webSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake handshakedata) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connectButton.setEnabled(false);
                        disconnectButton.setEnabled(true);
                        addressText.setEnabled(false);
                        displayString("Opened");
                    }
                });

                Message message = new Message();
                message.sender = MY_ID;
                message.recipient = "server";
                message.message = "Hello from " + Build.MANUFACTURER + " " + Build.MODEL;

                try {
                    String jsonMessage = objectMapper.writeValueAsString(message);
                    this.send(jsonMessage);
                } catch (JsonProcessingException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onMessage(final String message) {
                Log.i("Websocket", message);
                try {
                    final Message parsedMessage = objectMapper.readValue(message, Message.class);
                    if(MY_ID.equals(parsedMessage.recipient)) {
                        runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                displayString(parsedMessage.message);
                            }
                        });
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void onClose(int code, String reason, boolean remote) {
                Log.i("Websocket", "Closed " + reason);
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        connectButton.setEnabled(true);
                        disconnectButton.setEnabled(false);
                        addressText.setEnabled(true);
                    }
                });
            }

            @Override
            public void onError(Exception ex) {
                Log.i("Websocket", "Error " + ex.getMessage());
            }
        };

        webSocketClient.connect();
    }

    public void displayString(String message) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show();
    }
    public void load() {
        AssetManager assetManager = context.getAssets();
        try {
            final InputStream inputStream = assetManager.open("favorites.json");
            final ObjectMapper objectMapper = new ObjectMapper();
            final JsonNode node = objectMapper.readValue(inputStream, JsonNode.class);
            if(node.isArray()) {
                final ArrayNode arrayNode = (ArrayNode) node;
                for(final JsonNode internalNode : arrayNode) {
                    final Favorite favorite = objectMapper.convertValue(internalNode, Favorite.class);
                    //displayString(favorite.getText());
                    final FavoriteButton favoriteButton = new FavoriteButton(favorite);
                    favoriteButtons.add(favoriteButton);
                }
            }
        } catch (IOException ioex) {
            ioex.printStackTrace();
        }
    }

}
