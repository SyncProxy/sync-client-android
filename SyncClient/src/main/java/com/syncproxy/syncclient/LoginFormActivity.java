package com.syncproxy.syncclient;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

public class LoginFormActivity extends Activity {
    private String TAG = "LoginFormActivity";
    private static SyncClient syncClient;

    public static void openLoginForm(SyncClient syncClient, String login) {
        LoginFormActivity.syncClient  = syncClient;
        Intent intent = new Intent(syncClient, LoginFormActivity.class);
        Bundle b = new Bundle();
        b.putString("login", login);
        intent.putExtras(b); //Put your id to your next Intent
        syncClient.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.login_form_layout);
        Button buttonOK = (Button)findViewById(R.id.button_ok);

        // Display default login
        Bundle b = getIntent().getExtras();
        if(b != null)
            ((EditText)findViewById(R.id.text_login)).setText(b.getString("login"));
/*
// TEMP !!!
((EditText)findViewById(R.id.text_login)).setText("demo@syncproxy.com");
((EditText)findViewById(R.id.text_password)).setText("demo123");
*/

        buttonOK.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            if ( LoginFormActivity.syncClient != null )
                LoginFormActivity.syncClient.sendAuthenticationRequest(((EditText)findViewById(R.id.text_login)).getText().toString(), ((EditText)findViewById(R.id.text_password)).getText().toString());
            finish();
            }
        });
        Button buttonCancel = (Button)findViewById(R.id.button_cancel);
        buttonCancel.setOnClickListener( new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if ( LoginFormActivity.syncClient != null )
                    LoginFormActivity.syncClient.authenticationCancelled();
            finish();
            }
        });
    }
}
