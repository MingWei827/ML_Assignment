package com.example.mingwei.mikebot;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Message;
import android.provider.ContactsContract;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.BottomSheetBehavior;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.helper.ItemTouchHelper;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.mingwei.mikebot.Adapters.MessageAdapter;
import com.example.mingwei.mikebot.Contract.MessageContract;
import com.example.mingwei.mikebot.DBHelper.MessageDBHelper;
import com.example.mingwei.mikebot.DataTypes.MessageData;
import com.example.mingwei.mikebot.UtilityPackage.Constants;
import com.bumptech.glide.Glide;
import com.example.mingwei.mikebot.R;

import org.alicebot.ab.AIMLProcessor;
import org.alicebot.ab.Bot;
import org.alicebot.ab.Chat;
import org.alicebot.ab.Graphmaster;
import org.alicebot.ab.MagicBooleans;
import org.alicebot.ab.MagicStrings;
import org.alicebot.ab.PCAIMLProcessorExtension;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Random;

public class MainActivity extends AppCompatActivity {
    String questionToEdit;
    String valueQ1, valueQ2, valueQ3, valueQ4, valueQ5, valueQ6, valueQ7, valueQ8, valueQ9;
    String savedQ1, savedQ2, savedQ3, savedQ4, savedQ5, savedQ6, savedQ7, savedQ8;
    int score;
    private EditText messageInputView;
    private ImageView botWritingView;
    private int timePerCharacter;
    private ImageView botSpeechToggle;
    private ImageView mic_button;
    private static final int REQUEST_CODE_SPEECH_INPUT = 1;
    boolean isAnsweringQuestionnaire = false;
    //    boolean isEditingQuestionnaire = false;
    public Bot bot;
    public static Chat chat;
    private boolean speechAllowed; // the flag for toggling speech engine

    private TextToSpeech textToSpeech;

    private SQLiteDatabase database;
    private MessageAdapter messageAdapter;

    SharedPreferences preferences;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        MessageDBHelper messageDBHelper = new MessageDBHelper(this);
        database = messageDBHelper.getWritableDatabase();

        timePerCharacter = 30 + (new Random().nextInt(30)); // 30 - 60

        messageInputView = findViewById(R.id.message_input_view);
        messageInputView.setOnKeyListener((v, keyCode, event) -> {
            if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode ==
                    KeyEvent.KEYCODE_ENTER)) {
                sendChatMessage();
                messageInputView.requestFocus();
                return true;
            }
            return false;
        });
        ImageView messageSendButton = findViewById(R.id.message_send_button);
        botWritingView = findViewById(R.id.bot_writing_view);
        final ImageView deleteChatMessages = findViewById(R.id.delete_chats);
        botSpeechToggle = findViewById(R.id.bot_speech_toggle);

        //speech to text
        mic_button = findViewById(R.id.speech_to_text_button);
        mic_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent intent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
                intent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault());
                intent.putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to text");
                try {
                    startActivityForResult(intent, REQUEST_CODE_SPEECH_INPUT);
                } catch (Exception e) {
                    Toast.makeText(MainActivity.this, " " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        });

        findViewById(R.id.textView_hi).setOnClickListener(view->{
            messageInputView.setText("Hi");
        });
        findViewById(R.id.textView_yes).setOnClickListener(view->{
            messageInputView.setText("Yes");
        });
        findViewById(R.id.textView_no).setOnClickListener(view->{
            messageInputView.setText("No");
        });


        RecyclerView recyclerView = findViewById(R.id.recycler_view);
        LinearLayoutManager linearLayoutManager = new LinearLayoutManager(this);
        linearLayoutManager.setReverseLayout(true);
        recyclerView.setLayoutManager(linearLayoutManager);
        messageAdapter = new MessageAdapter(this, getAllMessages());
        recyclerView.setAdapter(messageAdapter);

        AssetManager assetManager = getResources().getAssets();
        File cacheDirectory = new File(getCacheDir().toString() + "/mr_paul/bots/darkbot");
        boolean dirMakingSuccessful = cacheDirectory.mkdirs();

        // saving the bot's core data in the cache
        if (dirMakingSuccessful && cacheDirectory.exists()) {
            try {
                for (String dir : assetManager.list("darkbot")) {
                    File subDirectory = new File(cacheDirectory.getPath() + "/" + dir);
                    subDirectory.mkdirs();
                    for (String file : assetManager.list("darkbot/" + dir)) {
                        File f = new File(cacheDirectory.getPath() + "/" + dir + "/" + file);
                        if (!f.exists()) {
                            InputStream in;
                            OutputStream out;

                            in = assetManager.open("darkbot/" + dir + "/" + file);
                            out = new FileOutputStream(cacheDirectory.getPath() + "/" + dir + "/" + file);

                            copyFile(in, out);
                            in.close();
                            out.flush();
                            out.close();
                        }
                    }
                }

            } catch (IOException e) {
                e.printStackTrace();
                Log.i("darkbot", "IOException occurred when writing from cache!");
            } catch (NullPointerException e) {
                Log.i("darkbot", "Nullpoint Exception!");
            }
        }

        // asking for permission for placing call
        if (ActivityCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED || ActivityCompat.checkSelfPermission(MainActivity.this,
                Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                requestPermissions(new String[]{Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE}, 0x12345);
            }
        }


        final ProgressDialog pd = new ProgressDialog(MainActivity.this);
        pd.setTitle("Please Wait");
        pd.setMessage("Initializing Bot...");
        pd.setCanceledOnTouchOutside(false);
        pd.setCancelable(false);

        // handler for communication with the background thread
        final Handler handler = new Handler() {
            @Override
            public void dispatchMessage(Message msg) {
                super.dispatchMessage(msg);
                pd.cancel();
            }
        };

        // initializing the bot in background thread
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                MagicStrings.root_path = getCacheDir().toString() + "/mr_paul";
                AIMLProcessor.extension = new PCAIMLProcessorExtension();
                bot = new Bot("darkbot", MagicStrings.root_path, "chat");
                chat = new Chat(bot);
                handler.sendMessage(new Message()); // dispatch a message to the UI thread
            }
        });

        // finally show the progress dialog box and start the thread
        pd.show();
        thread.start();

        // listen for button click
        messageSendButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                sendChatMessage();
            }
        });

        // initialization of speech engine
        textToSpeech = new TextToSpeech(this, new TextToSpeech.OnInitListener() {
            @Override
            public void onInit(int status) {
                if (status == TextToSpeech.SUCCESS) {
                    int result = textToSpeech.setLanguage(Locale.getDefault());
                    if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                        Toast.makeText(MainActivity.this,
                                "Default Language not recognized!", Toast.LENGTH_SHORT).show();
                        Log.i("darkbot", "Speech Engine not initialized");
                    } else {
                        preferences = getSharedPreferences(Constants.SHARED_PREFERENCES, MODE_PRIVATE);
                        /*Initially keep speech turned off*/
                        boolean wasSpeechAllowed = preferences.getBoolean(Constants.WAS_SPEECH_ALLOWED, true);
                        speechAllowed = wasSpeechAllowed;

                        if (wasSpeechAllowed) {
                            // show the mute button
                            botSpeechToggle.setImageResource(R.drawable.ic_mute_button);

                        } else {
                            // show the volume up button
                            botSpeechToggle.setImageResource(R.drawable.ic_volume_up_button);
                        }

                    }
                }
            }
        });

        deleteChatMessages.setOnClickListener(v -> deleteAllChatData());

        botSpeechToggle.setOnClickListener(v -> {

            if (speechAllowed) {
                speechAllowed = false;
                // show the volume up button - currently the bot is mute
                botSpeechToggle.setImageResource(R.drawable.ic_volume_up_button);
            } else {
                speechAllowed = true;
                // show the mute button - currently the bot is speaking
                botSpeechToggle.setImageResource(R.drawable.ic_mute_button);
            }

            // finally write the settings to the shared preference
            preferences.edit().putBoolean(Constants.WAS_SPEECH_ALLOWED, speechAllowed).apply();

        });

        // delete a particular message by just swiping right
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView, @NonNull RecyclerView.ViewHolder viewHolder, @NonNull RecyclerView.ViewHolder viewHolder1) {
                return false;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int i) {
                removeItem((long) viewHolder.itemView.getTag());
            }
        }).attachToRecyclerView(recyclerView);
    }

    // method to delete a single chat message
    private void removeItem(long id) {
        database.delete(MessageContract.MessageEntry.TABLE_NAME,
                MessageContract.MessageEntry._ID + "=" + id, null);
        messageAdapter.swapCursor(getAllMessages());
    }

    // method to delete all the chat data
    private void deleteAllChatData() {
        // ask for user confirmation
        AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
        builder.setMessage("Are you sure, You want to delete all the chats?");
        builder.setPositiveButton("Yes", (dialog, which) -> {
            database.execSQL("DELETE FROM " + MessageContract.MessageEntry.TABLE_NAME);
            messageAdapter.swapCursor(getAllMessages());

            Toast.makeText(MainActivity.this,
                    "All chats deleted!", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(MainActivity.this, MainActivity.class));
            finish();
        });

        builder.setNegativeButton("Nopes", (dialog, which) -> {
            // do nothing
        });

        AlertDialog alertDialog = builder.create();
        alertDialog.show();
    }


    private Cursor getAllMessages() {
        return database.query(
                MessageContract.MessageEntry.TABLE_NAME,
                null,
                null,
                null,
                null,
                null,
                MessageContract.MessageEntry._ID + " DESC"
        );
    }

    //Check number
    private boolean isNumber(String str) {
        if (str == null || str.length() == 0) {
            return false;
        }
        for (int i = 0; i < str.length(); i++) {
            if (!Character.isDigit(str.charAt(i))) {
                return false;
            }
        }
        return true;
    }


    // message sending method
    public void sendChatMessage() {
        String message = messageInputView.getText().toString().trim();
        if (message.isEmpty()) {
            messageInputView.setError("Can't send empty message!");
            messageInputView.requestFocus();
            return;
        }

        if(savedQ5 != null && savedQ5.equalsIgnoreCase("1") && (message.contains("gestational diabetes") || message.contains("Gestational Diabetes") || message.contains("Gestational") || message.contains("gestational"))){
            messageInputView.setError("Detected you are man and you are not allowed to edit Gestational Diabetes!");
            messageInputView.requestFocus();
            return;
        }

        if(valueQ1 != null && !valueQ1.equalsIgnoreCase("unknown") && (valueQ2 == null || valueQ2.equalsIgnoreCase("unknown"))){
            boolean hasNumber = false;
            String[] input = message.split(" ");
            for(String check:input){
                if(check.contains("-")){
                    messageInputView.setError("No negative value is allowed!");
                    messageInputView.requestFocus();
                    return;
                }
                if(isNumber(check)){
                    hasNumber = true;
                    int age = Integer.parseInt(check);
                    if(age < 1 || age > 100){
                        messageInputView.setError("Please enter a valid age in digit! E.g 22");
                        messageInputView.requestFocus();
                        return;
                    }
                    break;
                }
            }
            if(!hasNumber){
                messageInputView.setError("Please enter a valid age in digit! E.g 22");
                messageInputView.requestFocus();
                return;
            }
        }

        if(valueQ5 != null && valueQ5.equalsIgnoreCase("0") && valueQ6 != null && !valueQ6.equalsIgnoreCase("unknown") && (valueQ7 == null || valueQ7.equalsIgnoreCase("unknown"))){
            boolean hasNumber = false;
            String[] input = message.split(" ");
            for(String check:input){
                if(check.contains("-")){
                    messageInputView.setError("No negative value is allowed!");
                    messageInputView.requestFocus();
                    return;
                }
                if(isNumber(check)){
                    hasNumber = true;
                    int height = Integer.parseInt(check);
                    if(height < 1){
                        messageInputView.setError("Please enter a valid height in CM! E.g 170 CM");
                        messageInputView.requestFocus();
                        return;
                    }
                    break;
                }
            }
            if(!hasNumber){
                messageInputView.setError("Please enter a valid height in CM! E.g 170 CM");
                messageInputView.requestFocus();
                return;
            }
        }

        if(valueQ5 != null && valueQ5.equalsIgnoreCase("1") && (valueQ6 == null || valueQ6.equalsIgnoreCase("unknown")) && (valueQ7 == null || valueQ7.equalsIgnoreCase("unknown"))){
            boolean hasNumber = false;
            String[] input = message.split(" ");
            for(String check:input){
                if(check.contains("-")){
                    messageInputView.setError("No negative value is allowed!");
                    messageInputView.requestFocus();
                    return;
                }
                if(isNumber(check)){
                    hasNumber = true;
                    int height = Integer.parseInt(check);
                    if(height < 1){
                        messageInputView.setError("Please enter a valid height in CM! E.g 170 CM");
                        messageInputView.requestFocus();
                        return;
                    }
                    break;
                }
            }
            if(!hasNumber){
                messageInputView.setError("Please enter a valid height in CM! E.g 170 CM");
                messageInputView.requestFocus();
                return;
            }
        }

        if(valueQ7 != null && !valueQ7.equalsIgnoreCase("unknown") && (valueQ8 == null || valueQ8.equalsIgnoreCase("unknown"))){
            boolean hasNumber = false;
            String[] input = message.split(" ");
            for(String check:input){
                if(check.contains("-")){
                    messageInputView.setError("No negative value is allowed!");
                    messageInputView.requestFocus();
                    return;
                }
                if(isNumber(check)){
                    hasNumber = true;
                    int weight = Integer.parseInt(check);
                    if(weight < 1){
                        messageInputView.setError("Please enter a valid weight in KG! E.g 55 kg");
                        messageInputView.requestFocus();
                        return;
                    }
                    break;
                }
            }
            if(!hasNumber){
                messageInputView.setError("Please enter a valid weight in KG! E.g 55 kg");
                messageInputView.requestFocus();
                return;
            }
        }

        DateFormat dateFormat = new SimpleDateFormat("hh:mm dd/MM/yyyy");
        String timeStamp = dateFormat.format(new Date());

        addMessage(new MessageData(Constants.USER, message, timeStamp));

        if (message.toUpperCase().startsWith("CALL")) {
            // calling a phone number as requested by user

            String[] replyForCalling = {
                    "Calling",
                    "Placing a call on",
                    "Definitely, calling",
                    "There we go, calling",
                    "Making a call to",
                    "Ji sir, calling"
            };

            String[] temp = message.split(" ", 2);
            displayBotReply(new MessageData(Constants.BOT, replyForCalling[new Random().nextInt(replyForCalling.length)] + " " + temp[1], timeStamp));
            makeCall(temp[1]);
        } else if (message.toUpperCase().startsWith("OPEN") || message.toUpperCase().startsWith("LAUNCH")) {
            // call intent to app, requested by user

            String[] replyForOpeningApp = {
                    "There we go, opening",
                    "Launching",
                    "Opening",
                    "Trying to open",
                    "Trying to launch",
                    "There we go, launching"
            };

            String[] temp = message.split(" ", 2);
            displayBotReply(new MessageData(Constants.BOT, replyForOpeningApp[new Random().nextInt(replyForOpeningApp.length)] + " " + temp[1], timeStamp));
            launchApp(getAppName(temp[1]));
        } else if (message.toUpperCase().startsWith("DELETE") || message.toUpperCase().startsWith("CLEAR")) {
            displayBotReply(new MessageData(Constants.BOT, "Okay! I will clear up everything for you!", timeStamp));
        } else if (message.toUpperCase().contains("JOKE")) {

            String[] replyForJokes = {
                    "Jokes coming right up...",
                    "Processing a hot'n'fresh joke, right for you!",
                    "There you go...",
                    "This might make you laugh...",
                    "My jokes are still in alpha, Hopefully soon they'll get beta, till then...",
                    "Jokes are my another speciality, there you go...",
                    "Jokes, you ask? This might make you laugh...",
                    "Trying to make you laugh...",
                    "You might find this funny...",
                    "Enjoy your joke..."
            };

            displayBotReply(new MessageData(Constants.BOT, replyForJokes[new Random().nextInt(replyForJokes.length)] + "\n" + mainFunction(message), timeStamp));

        } else {
            // chat with bot - save the reply from the bot
            String botReply = mainFunction(message);
            if (botReply.trim().isEmpty()) {
                botReply = mainFunction("UDC");
            }
            displayBotReply(new MessageData(Constants.BOT, botReply, timeStamp));
        }

        messageInputView.setText("");

    }

    // displayBotReply() method
    private void displayBotReply(final MessageData messageData) {

        botWritingView.setVisibility(View.VISIBLE);
        Glide.with(MainActivity.this).asGif().load(R.drawable.bot_animation).into(botWritingView);

        final String message = messageData.getMessage();
        int lengthOfMessage = message.length();

        int timeToWriteInMillis = lengthOfMessage * timePerCharacter; // each character taking 10ms to write
        if (timeToWriteInMillis > 3000) {
            timeToWriteInMillis = 3000;
        } // not letting go beyond 3 secs

//        Handler handler = new Handler();
//        handler.postDelayed(new Runnable() {
//            @Override
//            public void run() {

        botWritingView.setVisibility(View.GONE);

        addMessage(messageData);

        if (messageData.getMessage().equals("Okay! I will clear up everything for you!")) {
            // the user requested to delete all chat data
            deleteAllChatData();
        }

        // speak out the bot reply
        if (speechAllowed) {
            textToSpeech.setSpeechRate(0.9f);
            textToSpeech.setPitch(1f);

            textToSpeech.speak(message, TextToSpeech.QUEUE_FLUSH, null);
        }

//            }
//        }, timeToWriteInMillis); // the delay is according to the length of message
        String diabetesTest = chat.predicates.get("test");
        isAnsweringQuestionnaire = diabetesTest.equals("1");
        questionToEdit = chat.predicates.get("edit");
//        isEditingQuestionnaire = diabetesEdit.equals("1");

        valueQ9 = chat.predicates.get("q9");
        if (!valueQ9.equalsIgnoreCase("unknown")) {
            int q9 = Integer.parseInt(valueQ9);
            if (q9 == 0) {
                addMessage(new MessageData(Constants.BOT, "Your score is " + score, messageData.getTimeStamp()));
                if (score >= 5) {
                    addMessage(new MessageData(Constants.BOT, "You are at increased risk for having prediabetes and are at high risk for type 2 diabetes as your score is 5 or higher. However, only your doctor can tell for sure if you have type 2 diabetes or prediabetes, a condition in which blood sugar levels are higher than normal but not high enough yet to be diagnosed as type 2 diabetes. Talk to your doctor to see if additional testing is needed.", messageData.getTimeStamp()));
                    addMessage(new MessageData(Constants.BOT, "Do you want to perform any other action?", messageData.getTimeStamp()));

                } else {
                    addMessage(new MessageData(Constants.BOT, "Congregates! You are at low risk of getting diabetes as your score is lower than 5!", messageData.getTimeStamp()));
                    addMessage(new MessageData(Constants.BOT, "Do you want to perform any other action?", messageData.getTimeStamp()));
                }


                removePredicates();
                clearData();
                return;
            }
        } else {
            if (isAnsweringQuestionnaire || !questionToEdit.equalsIgnoreCase("unknown")) {
                valueQ1 = chat.predicates.get("q1");
                valueQ2 = chat.predicates.get("q2");
                valueQ3 = chat.predicates.get("q3");
                valueQ4 = chat.predicates.get("q4");
                valueQ5 = chat.predicates.get("q5");
                valueQ6 = chat.predicates.get("q6");
                valueQ7 = chat.predicates.get("q7");
                valueQ8 = chat.predicates.get("q8");

                if (!questionToEdit.equalsIgnoreCase("unknown")) {
                    if (questionToEdit.equalsIgnoreCase("1")) {
//                    chat.predicates.remove("edit");
                        savedQ1 = chat.predicates.get("rq1");
                        valueQ1 = savedQ1;
                    } else { // if (valueQ1.equalsIgnoreCase("unknown")) {
                        valueQ1 = savedQ1;
                    }

                    if (questionToEdit.equalsIgnoreCase("2")) {
                        savedQ2 = chat.predicates.get("rq2");
                        valueQ2 = savedQ2;
                    } else { // if (valueQ1.equalsIgnoreCase("unknown")) {
                        valueQ2 = savedQ2;
                    }

                    if (questionToEdit.equalsIgnoreCase("3")) {
                        savedQ3 = chat.predicates.get("rq3");
                        valueQ3 = savedQ3;
                    } else { // if (valueQ1.equalsIgnoreCase("unknown")) {
                        valueQ3 = savedQ3;
                    }

                    if (questionToEdit.equalsIgnoreCase("4")) {
                        savedQ4 = chat.predicates.get("rq4");
                        valueQ4 = savedQ4;
                    } else {
                        valueQ4 = savedQ4;
                    }

                    if (questionToEdit.equalsIgnoreCase("5")) { // || questionToEdit.equalsIgnoreCase("6") ) {
                        savedQ5 = chat.predicates.get("rq5");
                        valueQ5 = savedQ5;

                        savedQ6 = chat.predicates.get("rq6");
                        valueQ6 = savedQ6;
                    } else {
                        valueQ5 = savedQ5;
                        valueQ6 = savedQ6;
                    }

                    if (questionToEdit.equalsIgnoreCase("6")) {
                        savedQ6 = chat.predicates.get("rq6");
                        valueQ6 = savedQ6;
                    } else {
                        valueQ6 = savedQ6;
                    }

                    if (questionToEdit.equalsIgnoreCase("7")) {
                        savedQ7 = chat.predicates.get("rq7");
                        valueQ7 = savedQ7;
                    } else {
                        valueQ7 = savedQ7;
                    }

                    if (questionToEdit.equalsIgnoreCase("8")) {
                        savedQ8 = chat.predicates.get("rq8");
                        valueQ8 = savedQ8;
                    } else {
                        valueQ8 = savedQ8;
                    }
                }

                Log.d("###", "displayBotReplyQ1: " + valueQ1);
                Log.d("###", "displayBotReplyQ2: " + valueQ2);
                Log.d("###", "displayBotReplyQ3: " + valueQ3);
                Log.d("###", "displayBotReplyQ4: " + valueQ4);
                Log.d("###", "displayBotReplyQ5: " + valueQ5);
                Log.d("###", "displayBotReplyQ6: " + valueQ6);
                Log.d("###", "displayBotReplyQ7: " + valueQ7);
                Log.d("###", "displayBotReplyQ8: " + valueQ8);
                Log.d("###", "===============================");
                Log.d("###", "displayBotReplyQ1: " + savedQ1);
                Log.d("###", "displayBotReplyQ2: " + savedQ2);
                Log.d("###", "displayBotReplyQ3: " + savedQ3);
                Log.d("###", "displayBotReplyQ4: " + savedQ4);
                Log.d("###", "displayBotReplyQ5: " + savedQ5);
                Log.d("###", "displayBotReplyQ6: " + savedQ6);
                Log.d("###", "displayBotReplyQ7: " + savedQ7);
                Log.d("###", "displayBotReplyQ8: " + savedQ8);

                score = 0;
                try {
                    int q1 = Integer.parseInt(valueQ1);
                    int q2 = Integer.parseInt(valueQ2);
                    int ageScore = 0;
                    if (q2 >= 40 && q2 < 50) {
                        ageScore = 1;
                    } else if (q2 >= 50 && q2 < 60) {
                        ageScore = 2;
                    } else if (q2 > 60) {
                        ageScore = 3;
                    }

                    int q3 = Integer.parseInt(valueQ3);
                    int q4 = Integer.parseInt(valueQ4);
                    int q5 = Integer.parseInt(valueQ5);
                    //int q6 = Integer.parseInt(valueQ6);
                    float q7 = Integer.parseInt(valueQ7);
                    float q8 = Integer.parseInt(valueQ8);
                    int q6 = -1;
                    if (q5 == 1) {
                        q6 = 0;
                    } else {
                        q6 = Integer.parseInt(valueQ6);
                    }

                    addMessage(new MessageData(Constants.BOT, "Family History:" + (q1 == 1 ? "Yes" : "No") + "\nAge:" + valueQ2 + "\nPhysically Active:" + (q3 == 1 ? "Yes" : "No") + "\nHigh Blood Pressure:" + (q4 == 1 ? "Yes" : "No") + "\nGender:" + (q5 == 1 ? "Man" : "Woman") + "\nGestational Diabetes(For woman only):" + (q5 ==1 ? "N/A": (q6 == 1 ? "Yes" : "No")) + "\nHeight:" + valueQ7 + "\nWeight:" + valueQ8, messageData.getTimeStamp()));

                    isAnsweringQuestionnaire = false;
                    questionToEdit = "";

                    addMessage(new MessageData(Constants.BOT, "Do you want to edit your answer? If yes which question? E.g:Age", messageData.getTimeStamp()));

                    savedQ1 = valueQ1;
                    savedQ2 = valueQ2;
                    savedQ3 = valueQ3;
                    savedQ4 = valueQ4;
                    savedQ5 = valueQ5;
                    savedQ6 = valueQ6;
                    savedQ7 = valueQ7;
                    savedQ8 = valueQ8;

                    score = q1 + ageScore + q3 + q4 + q5 + q6 + weightCategory(q7, q8);

                    removePredicates();


                } catch (Exception e) {
                    //display
                    e.printStackTrace();
                }
            } else {
                removePredicates();
            }
        }
        messageInputView.requestFocus();
    }

    private void addMessage(MessageData messageData) {
        String sender = messageData.getSender();
        String message = messageData.getMessage();
        String timestamp = messageData.getTimeStamp();

        ContentValues contentValues = new ContentValues();
        contentValues.put(MessageContract.MessageEntry.COLUMN_SENDER, sender);
        contentValues.put(MessageContract.MessageEntry.COLUMN_MESSAGE, message);
        contentValues.put(MessageContract.MessageEntry.COLUMN_TIMESTAMP, timestamp);

        database.insert(MessageContract.MessageEntry.TABLE_NAME, null, contentValues);
        messageAdapter.swapCursor(getAllMessages());
    }

    // UTILITY METHODS

    //Calculate weight category
    public int weightCategory(float height, float weight) {
        int heightWeightScore = 0;
        if (height >= 150 && height < 156) {
            if (weight >= 56.25 && weight <= 71.21) {
                heightWeightScore = 1;
            } else if (weight >= 67.13 && weight <= 95.25) {
                heightWeightScore = 2;
            } else if (weight > 95.25) {
                heightWeightScore = 3;
            }
        } else if (height >= 156 && height < 164) {
            if (weight >= 61.69 && weight <= 78.47) {
                heightWeightScore = 1;
            } else if (weight >= 74.39 && weight <= 104.78) {
                heightWeightScore = 2;
            } else if (weight > 104.78) {
                heightWeightScore = 3;
            }
        } else if (height >= 164 && height < 171) {
            if (weight >= 68.04 && weight <= 86.18) {
                heightWeightScore = 1;
            } else if (weight >= 81.65 && weight <= 115.21) {
                heightWeightScore = 2;
            } else if (weight > 115.21) {
                heightWeightScore = 3;
            }
        } else if (height >= 171 && height < 179) {
            if (weight >= 74.39 && weight <= 94.35) {
                heightWeightScore = 1;
            } else if (weight >= 89.36 && weight <= 125.65) {
                heightWeightScore = 2;
            } else if (weight > 125.65) {
                heightWeightScore = 3;
            }
        } else if (height >= 179 && height < 186) {
            if (weight >= 81.19 && weight <= 102.51) {
                heightWeightScore = 1;
            } else if (weight >= 97.52 && weight <= 136.53) {
                heightWeightScore = 2;
            } else if (weight > 136.53) {
                heightWeightScore = 3;
            }
        } else if (height >= 186 && height < 194) {
            if (weight >= 88.00 && weight <= 111.13) {
                heightWeightScore = 1;
            } else if (weight >= 105.69 && weight <= 148.33) {
                heightWeightScore = 2;
            } else if (weight > 148.33) {
                heightWeightScore = 3;
            }
        }

        return heightWeightScore;
    }

    private void removePredicates() {
        chat.predicates.remove("q1");
        chat.predicates.remove("q2");
        chat.predicates.remove("q3");
        chat.predicates.remove("q4");
        chat.predicates.remove("q5");
        chat.predicates.remove("q6");
        chat.predicates.remove("q7");
        chat.predicates.remove("q8");
        chat.predicates.remove("q9");
        chat.predicates.remove("rq1");
        chat.predicates.remove("rq2");
        chat.predicates.remove("rq3");
        chat.predicates.remove("rq4");
        chat.predicates.remove("rq5");
        chat.predicates.remove("rq6");
        chat.predicates.remove("rq7");
        chat.predicates.remove("rq8");
        chat.predicates.remove("test");
        chat.predicates.remove("edit");
    }

    private void clearData(){
        savedQ1 = null;
        savedQ2 = null;
        savedQ3 = null;
        savedQ4 = null;
        savedQ5 = null;
        savedQ6 = null;
        savedQ7 = null;
        savedQ8 = null;
    }


    // copying the file
    private void copyFile(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    // responding of bot to user's requests
    public static String mainFunction(String args) {

        MagicBooleans.trace_mode = false;
        Graphmaster.enableShortCuts = true;

        return chat.multisentenceRespond(args);
    }

    // functionality of the bot

    // method for searching a name in user's contact list
    public String getNumber(String name, Context context) {

        String number = "";
        Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
        String[] projection = new String[]{ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME, ContactsContract.CommonDataKinds.Phone.NUMBER};

        Cursor people = context.getContentResolver().query(uri, projection, null, null, null);
        if (people == null) {
            return number;
        }

        int indexName = people.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
        int indexNumber = people.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);

        people.moveToFirst();
        do {
            String Name = people.getString(indexName);
            String Number = people.getString(indexNumber);
            if (Name.equalsIgnoreCase(name)) {
                return Number.replace("-", "");
            }
        } while (people.moveToNext());

        people.close();

        return number;
    }

    // method for placing a call
    private void makeCall(String name) {


        try {
            String number;

            if (name.matches("[0-9]+") && name.length() > 2) {
                // string only contains number
                number = name;
            } else {
                number = getNumber(name, MainActivity.this);
            }


            Intent callIntent = new Intent(Intent.ACTION_CALL);
            callIntent.setData(Uri.parse("tel:" + number));
            startActivity(callIntent);
        } catch (SecurityException e) {
            Toast.makeText(this, "Calling Permission - DENIED!", Toast.LENGTH_SHORT).show();
        }
    }

    // method for searching through all the apps in the user's phone
    public String getAppName(String name) {
        name = name.toLowerCase();

        PackageManager pm = getPackageManager();
        List<ApplicationInfo> l = pm.getInstalledApplications(PackageManager.GET_META_DATA);

        for (ApplicationInfo ai : l) {
            String n = pm.getApplicationLabel(ai).toString().toLowerCase();
            if (n.contains(name) || name.contains(n)) {
                return ai.packageName;
            }
        }

        return "package.not.found";
    }

    // method for launching an app
    protected void launchApp(String packageName) {
        Intent mIntent = getPackageManager().getLaunchIntentForPackage(packageName);

        if (packageName.equals("package.not.found")) {
            Toast.makeText(getApplicationContext(), "I'm afraid, there's no such app!", Toast.LENGTH_SHORT).show();
        } else if (mIntent != null) {
            try {
                startActivity(mIntent);
            } catch (Exception err) {
                Log.i("darkbot", "App launch failed!");
                Toast.makeText(this, "I'm afraid, there's no such app!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    //speech to text button
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQUEST_CODE_SPEECH_INPUT) {
            if (resultCode == RESULT_OK && data != null) {
                ArrayList<String> result = data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
                messageInputView.setText(Objects.requireNonNull(result).get(0));
            }
        }
    }

}
