package com.syncproxy.syncclient;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

import tech.gusavila92.websocketclient.WebSocketClient;

public class SyncClient extends Activity {
    private static final String TAG = "SyncClient";
    public static SyncClient defaultClient;
    private static final String ClientVersion = "1.0";
    static final int LOGIN_PROMPT_REQUEST = 1;
    private int SOCKET_CONNECT_TIMEOUT = 5000;
    private int SOCKET_CONNECT_RETRY = 10000;
    public Object params[][] = {
            {"protocol", "wss"},
            {"serverUrl", "my.syncproxy.com"},
            {"serverPort", 4501},
            {"proxyId", ""},
            {"connectorType", "SQLite"},
            {"dbName", null},
            {"autoUpgradeDB", true},
            {"autoInit", true},
            {"reactiveSync", true},
            {"tablesToSync", new String[0]},
            {"login", ""},
            {"welcomeMessage", "To begin, please press Sync button"},
//            {"utcDates", true}
    };

    public enum LogLevel {info, warning, error, critical}

    ;

    public enum Steps {
        Idle,
        PromptingAuthentication,
        Authenticating,
        AuthenticationFailed,
        RequestingSchemaUpgrade,
        RequestingSyncProfile,
        GettingClientChanges,
        SendingClientChanges,
        ClientChangesSent,
        RequestingServerChanges,
        FinishingSync,
        SyncFinished
    };

    public enum ConnectionStatus {
        Offline,
        Online,
        Connected
    };

    public enum SocketClientStatus {
        Disconnected,
        Connected
    };

    public enum ButtonStatus {
        Offline,
        Online,
        Sync_Not_Authenticated,
        Sync_In_Progress,
        Sync_Auto_In_Progress,
        Sync_OK,
        Sync_Auto,
        Sync_Error
    }

    ;
    public static Activity srcActivity;
    private static Boolean initDone = false;
    public BroadcastReceiver networkNotificationsReceiver;
    Thread syncThread;
    private WebSocketClient socketClient;
    Steps currentStep = Steps.Idle;
    private BaseConnector connector;
    private String login, password, sessionId;
    SharedPreferences syncConfig;
    public Schema schema;
    public SyncProfile syncProfile;
    private static List<SyncButton> syncButtons = new ArrayList<SyncButton>();
    public static ConnectionStatus connectionStatus = ConnectionStatus.Offline;
    public static SocketClientStatus socketClientStatus = SocketClientStatus.Disconnected;
    private static Boolean lastSyncFailed = false;
    List<String> handledTables = new ArrayList<String>();
    private static Changes currentChanges;      // detected and ready-to-send client changes

    public enum SyncType {
        ClientReactiveSync,
        ServerReactiveSync,
        FullSync
    }

    private SyncType syncType = SyncType.FullSync;

    public SyncClient() {
        defaultClient = this;
    }

    ////////////////////////////////////////////
    // Launch sync client from other activity //
    ////////////////////////////////////////////
    public static SyncClient startObservation(Activity activity, String sParams) {
        Intent intent = new Intent(activity, SyncClient.class);
        intent.putExtra(Intent.EXTRA_TEXT, sParams);
        activity.startActivity(intent);
        return new SyncClient();
    }

    public static SyncClient startObservation(Activity activity, String sParams, ViewGroup mainView) {
        startObservation(activity, sParams);
        return new SyncClient();
    }

    ///////////////////////
    // Start sync client //
    ///////////////////////
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        syncConfig = PreferenceManager.getDefaultSharedPreferences(this);

        if (initDone) {
            finish();
            return;
        }
        initDone = true;
        Intent i = getIntent();
        String sParams = i.getStringExtra(Intent.EXTRA_TEXT);

        JSONObject syncParams;
        try {
            syncParams = new JSONObject(sParams);
        } catch (JSONException e) {
            SyncClient.log(TAG, e.getMessage(), LogLevel.error);
            e.printStackTrace();
            finish();
            return;
        }
        if (syncParams != null) {
            Iterator<String> keys = syncParams.keys();
            while (keys.hasNext()) {
                String key = keys.next();
                String val = null;
                try {
                    val = syncParams.getString(key);
                } catch (JSONException e) {
                    Log.d(TAG, e.getMessage());
                    e.printStackTrace();
                    return;
                }
                setParam(key, val);
            }
        }
        logAllParams();

        if (getParam("proxyId").equals("")) {
            showAlert("Error: missing proxyId");
            return;
        }
        if (getParam("connectorType").equals("SQLite"))
            connector = (SQLiteConnector) new SQLiteConnector(this, (String) getParam("dbName"));
        else {
            showAlert("Unknown connectorType");
            return;
        }
        // Try to retrieve login from users items first, then from app's params
        if ( !getItem("login").equals("") )
            login = getItem("login");
        else {
            if (!getParam("login").equals(""))
                login = getParam("login").toString();
            // First sync: display welcome message
            Toast.makeText(getApplicationContext(), translate((String)getParam("welcomeMessage")), Toast.LENGTH_LONG).show();
        }
        try {
            loadSchema();
            loadSyncProfile();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        if (schema != null) {
            try {
                Log.d(TAG, "Loaded schema: " + schema.toJSON());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        if (syncProfile != null) {
            try {
                Log.d(TAG, "Loaded syncProfile: " + syncProfile.toJSON());
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }
        registerNetworkNotifications();
    }

    protected void onStart() {
        super.onStart();
        finish();
    }

    private void setParam(String paramName, Object val) {
        for (int p = 0; p < params.length; p++) {
            if (params[p][0].equals(paramName)) {
                params[p][1] = val;
                return;
            }
        }
    }

    public Object getParam(String paramName) {
        for (int p = 0; p < params.length; p++) {
            if (params[p][0] == paramName)
                return params[p][1];
        }
        return null;
    }

    private void logAllParams() {
        for (int p = 0; p < params.length; p++)
            Log.d(TAG, "Param #" + p + " " + params[p][0] + ": " + params[p][1]);
    }

    private Boolean isConnected() {
        ConnectivityManager cm = (ConnectivityManager) getApplicationContext().getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        if (null != activeNetwork) {
            if (activeNetwork.getType() != 0)
                return true;
        }
        return false;
    }

    private void registerNetworkNotifications() {
        networkNotificationsReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Bundle extras = intent.getExtras();
                if (extras != null) {
                    for (String key : extras.keySet()) {
                        String val = extras.get(key).toString();
                        if ((key.equals("noConnectivity")) && (val.equals("true")))
                            onDisconnected();
                        if ((key.equals("networkInfo")) && (val.indexOf("state: CONNECTED") > -1))
                            onConnected();
                    }
                }
            }
        };
        IntentFilter filter = new IntentFilter();
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        getApplicationContext().registerReceiver(networkNotificationsReceiver, filter);
    }

    private String getServerUrl() {
        String port = "";
        if (!getParam("serverPort").equals(""))
            port = ":" + getParam("serverPort");
        return getParam("protocol") + "://" + getParam("serverUrl") + port + "/";
    }
/*
    // Try to reconnect the websocket every X milliseconds
    private void reconnectSocketClient() {
        final Handler handler = new Handler();
        Runnable runnableCode = new Runnable() {
            @Override
            public void run() {
                if ( socketClientStatus == SocketClientStatus.Disconnected )
                    socketClient.enableAutomaticReconnection();
                handler.postDelayed(this, 10000);
            }
        };
        handler.post(runnableCode);
    }
*/

    private void onConnected() {
        connectionStatus = ConnectionStatus.Online;
        lastSyncFailed = false;     // reset possible previous sync error red button
        updateSyncButton();

        // Init a web socket with server
        URI uri;
        try {
            uri = new URI(getServerUrl());
            Log.d(TAG, "server uri: " + uri);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        socketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen() {
                socketClientStatus = SocketClientStatus.Connected;
                lastSyncFailed = false;
                updateSyncButton();
                startSync(SyncType.FullSync);       // on (re)connection, always perform a full syncs
            }

            @Override
            public void onTextReceived(String message) {
                try {
                    parseServerResponse(message);
                } catch (JSONException e) {
                    e.printStackTrace();
                } catch (Exception e) {
                    log(TAG, "onTextReceived exception", LogLevel.error);
                    Log.d(TAG, e.getMessage());
                    e.printStackTrace();
                    lastSyncFailed = true;
                    currentStep = Steps.Idle;
                    socketClientStatus = SocketClientStatus.Disconnected;
                    updateSyncButton();
                }
            }

            @Override
            public void onBinaryReceived(byte[] data) {
                Log.d(TAG, "onBinaryReceived");
            }

            @Override
            public void onException(Exception e) {
                log(TAG, "WebSocketClient onException", LogLevel.error);
                Log.d(TAG, e.getMessage());
                lastSyncFailed = true;
                socketClientStatus = SocketClientStatus.Disconnected;
                currentStep = Steps.Idle;
                updateSyncButton();
            }

            @Override
            public void onCloseReceived() {
                Log.d(TAG, "onCloseReceived");
                connectionStatus = ConnectionStatus.Online;     // online but not connected to server anymore
                lastSyncFailed = true;
                currentStep = Steps.Idle;
                socketClientStatus = SocketClientStatus.Disconnected;
                updateSyncButton();
            }

            @Override
            public void onPingReceived(byte[] data) {
                Log.d(TAG, "onPingReceived");
            }

            @Override
            public void onPongReceived(byte[] data) {
                Log.d(TAG, "onPongReceived");
            }
        };
        socketClient.setConnectTimeout(SOCKET_CONNECT_TIMEOUT);
        socketClient.enableAutomaticReconnection(SOCKET_CONNECT_RETRY);
        socketClient.connect();
    }

    private void onDisconnected() {
        // client disconnected: next sync will necessarily be a full sync
        Log.d(TAG, "onDisconnected");
        connectionStatus = ConnectionStatus.Offline;
        syncType = SyncType.FullSync;
        currentStep = Steps.Idle;
        updateSyncButton();
    }

    public void authenticationCancelled() {
        currentStep = Steps.Idle;
        updateSyncButton();
    }

    private void sendRequest(String request) {
        updateSyncButton();
        request = "\"proxyId\":\"" + getParam("proxyId") + "\"," + request;
        String clientCode = getItem("clientCode");
        if (!clientCode.isEmpty())
            request += ", \"clientCode\":\"" + clientCode + "\"";
        socketClient.send("{" + request + "}");
    }

    private void sendRequestWithSessionId(String request) {
        request = request + ",\"sessionId\":\"" + sessionId + "\"";
        sendRequest(request);
    }

    public void sendAuthenticationRequest(String login, String password) {
        this.login = login;
        setItem("login", login);        // save login in users items
        this.password = password;
        currentStep = Steps.Authenticating;
        if (login.isEmpty())
            return;
        String request = "\"login\":\"" + login + "\", \"password\":\"" + password + "\"";
        sendRequest(request);
    }

    private Steps getNextStep() {
        switch (currentStep) {
            case PromptingAuthentication:
                return Steps.Authenticating;
            case Authenticating:
                if (!isAuthenticated())
                    return Steps.AuthenticationFailed;
                showAlert("Sync started");
                switch (syncType) {
                    case FullSync:
                        return Steps.RequestingSchemaUpgrade;
                    case ServerReactiveSync:
                        return Steps.RequestingServerChanges;
                    default:
                        return Steps.GettingClientChanges;
                }
            case RequestingSchemaUpgrade:
                switch (syncType) {
                    case ClientReactiveSync:
                        return Steps.GettingClientChanges;
                    case ServerReactiveSync:
                        return Steps.RequestingServerChanges;
                    default:
                        return Steps.RequestingSyncProfile;
                }
            case RequestingSyncProfile:
                return Steps.GettingClientChanges;
            case GettingClientChanges:
                return Steps.SendingClientChanges;
            case SendingClientChanges:
                return Steps.ClientChangesSent;
            case ClientChangesSent:
                switch (syncType) {
                    case ClientReactiveSync:
                        return Steps.FinishingSync;
                    default:
                        return Steps.RequestingServerChanges;
                }
            case RequestingServerChanges:
//                return Steps.SavingServerChanges;
//            case SavingServerChanges:
                return Steps.FinishingSync;
            case FinishingSync:
                return Steps.SyncFinished;
            case SyncFinished:
            case Idle:
                return Steps.Idle;
        }
        return null;
    }

    private void processNextStep() throws JSONException {
        currentStep = getNextStep();
        switch (currentStep) {
            case Idle:
                break;
            case PromptingAuthentication:
                break;
            case Authenticating:
                break;
            case AuthenticationFailed:
                showAlert("Authentication failure");
                sessionId = null;
                login = null;
                password = null;
                authenticate();
                break;
            case RequestingSchemaUpgrade:
                requestSchemaUpgrade();
                break;
            case RequestingSyncProfile:
                requestSyncProfile();
                break;
            case GettingClientChanges:
                getAndSendClientChanges();
                break;
            case SendingClientChanges:
                break;
            case ClientChangesSent:
                resetSentChanges();
                processNextStep();
                break;
            case RequestingServerChanges:
                requestServerChanges(null);
                break;
//            case SavingServerChanges:
//                break;
            case FinishingSync:
                endServerSync();
                break;
            case SyncFinished:
                onSyncEnd();
                break;
        }
    }

    private void parseServerResponse(String response) throws Exception {
        JSONObject data;
        try {
            data = new JSONObject(response);
            Log.d(TAG, "Server response: " + data.toString());
        } catch (Throwable t) {
            Log.d(TAG, "Invalid server response: \"" + response + "\"");
            showAlert("Invalid server response");
            return;
        }
        handleServerResponse(data);
        processNextStep();
    }

    private void handleServerResponse(JSONObject data) throws Exception {
        if (data.has("err") && (data.getString("err").equals("AUTH FAILURE"))) {
            currentStep = Steps.AuthenticationFailed;
            return;
        }
        switch (currentStep) {
            case Authenticating:
                String clientCode = null;
                if (data.has("clientCode")) {
                    clientCode = data.getString("clientCode");
                    Log.d(TAG, "clientCode:" + clientCode);
                    setItem("clientCode", clientCode);
                }
                if (data.has("sessionId")) {
                    sessionId = data.getString("sessionId");
                    Log.d(TAG, "sessionId:" + sessionId);
                } else
                    sessionId = null;
                break;
            case RequestingSchemaUpgrade:
                JSONObject jsonSchema = data.getJSONObject("schema");
                if ((jsonSchema != null) && (jsonSchema.has("version"))) {
                    Log.d(TAG, "Schema upgrade received: " + jsonSchema.toString());
                    schema = new Schema(jsonSchema);
                    saveSchema();
                    if (upgradeNeeded(jsonSchema))
                        upgradeDatabase(jsonSchema);
                }
                break;
            case RequestingSyncProfile:
                JSONObject syncRules = data.getJSONObject("syncRules");
                Log.d(TAG, "Sync rules received: " + syncRules.toString());
                cacheSyncProfile(syncRules);
                break;
            case RequestingServerChanges:
                if ( data.has("Deletes") || data.has("Inserts") || data.has("Updates") )
                    handleServerChanges(data);
                break;
            case Idle:
                if (data.has("serverSync")) {
                    // A reactive server sync has been initiated by server. Tables to sync are indicated in serverSync array.
                    startSync(SyncType.ServerReactiveSync);
                }
            default:
                if ( data.has("Deletes") || data.has("Inserts") || data.has("Updates") )
                    handleServerChanges(data);
                break;
        }
    }

    private void handleServerChanges(JSONObject data) throws JSONException {
        connector.suspendChangesDetection();
        if (data.has("Deletes")) {
            JSONObject deletes = data.getJSONObject("Deletes");
            handleServerDeletes(deletes);
        }
        JSONObject updates = null, inserts = null;
        if (data.has("Updates"))
            updates = data.getJSONObject("Updates");
        if (data.has("Inserts"))
            inserts = data.getJSONObject("Inserts");
        if ((updates != null) || (inserts != null))
            handleServerUpdatesAndInserts(updates, inserts);
        connector.resumeChangesDetection();
    }

    private void authenticate() throws JSONException {
        currentStep = Steps.PromptingAuthentication;
        if (isAuthenticated() || getCredentials()) {
            if (isAuthenticated())
                currentStep = Steps.Authenticating;
        } else
            currentStep = Steps.Idle;
        processNextStep();
    }

    private Boolean getCredentials() {
        if ((login != null) && (password != null))
            return true;
        promptUserLogin();
        return false;
    }

    /*
        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent resultIntent) {
            Log.d(TAG, "onActivityResult");
            if (requestCode == LOGIN_PROMPT_REQUEST) {
                if (resultCode == Activity.RESULT_OK) {
                    Uri loginPassword = resultIntent.getData();
                    sendAuthenticationRequest(resultIntent.getStringExtra("login"), resultIntent.getStringExtra("password"));
                }
                else if (resultCode == Activity.RESULT_CANCELED){
                    currentStep = Steps.Idle;
                    finish();
                }
            }
        }
    */
    private void promptUserLogin() {
        //Intent intent = new Intent(this, LoginFormActivity.class);
        currentStep = Steps.PromptingAuthentication;
        LoginFormActivity.openLoginForm(this, getItem("login"));
//        startActivity(intent, LOGIN_PROMPT_REQUEST);
    }

    private Boolean isAuthenticated() {
        return (sessionId != null);
    }

    public void setItem(String key, String value) {
        SharedPreferences.Editor syncConfigEditor = syncConfig.edit();
        syncConfigEditor.putString(getParam("proxyId") + "." + key, value);
        syncConfigEditor.commit();
    }

    public String getItem(String key) {
        return syncConfig.getString(getParam("proxyId") + "." + key, "");
    }

    private JSONObject loadJSONItem(String key) throws JSONException {
        String sItem = getItem(key);
        if ((sItem != null) && !sItem.isEmpty())
            return new JSONObject(sItem);
        else
            return null;
    }

    public void startSync(SyncType syncType) {
        if (currentStep != Steps.Idle) {
            Log.d(TAG, "Sync already started");
            return;
        }
        this.syncType = syncType;
        syncThread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    sync();
                } catch (JSONException e) {
                    e.printStackTrace();
                }
            }
        });
        syncThread.start();
    }

    public void sync() throws JSONException {
        currentChanges = null;      // reset changes to be sent
        lastSyncFailed = false;
        authenticate();
    }


    //////////////////////////
    // Sync button display //
    //////////////////////////
    public static void showSyncButton(Activity activity) {
        showSyncButton(activity, -1, -1, -1);
    }

    private ButtonStatus getButtonStatus() {
        Log.d(TAG, "currentStep=" + currentStep);
        Log.d(TAG, "lastSyncFailed=" + lastSyncFailed);
        Log.d(TAG, "connectionStatus=" + connectionStatus);
        Log.d(TAG, "socketClientStatus=" + socketClientStatus);

        if ( (socketClientStatus == SocketClientStatus.Disconnected) || (lastSyncFailed == true) )
            return ButtonStatus.Sync_Error;
        else if (connectionStatus == ConnectionStatus.Offline)
            return ButtonStatus.Offline;
        else {
            // Online
//            if ( !this.mustUpgrade ){
            Boolean isConnected = this.isConnected();
            if (!isConnected)
                return ButtonStatus.Online;
            if (!this.isAuthenticated())
                return ButtonStatus.Sync_Not_Authenticated;
            Boolean reactive = hasReactiveSync();
            if (reactive)
                return ButtonStatus.Sync_Auto;
            else
                return ButtonStatus.Sync_OK;
//            }
        }
    }

    public void updateSyncButton() {
        ButtonStatus buttonStatus = defaultClient.getButtonStatus();
        Boolean reactive = this.hasReactiveSync();
        if (buttonStatus == ButtonStatus.Sync_Auto)
            connector.startChangesDetection();
            // Sync Auto disabled: stop change detection
        else if (!reactive || (currentStep == Steps.Idle))
            connector.stopChangesDetection();
        SyncButton.setStatusForAllButtons(syncButtons, buttonStatus, this, (currentStep != Steps.Idle));
    }

    public static void showSyncButton(Activity activity, int gravity, int x, int y) {
        // Retrieve (or create) sync button used by activity
        for (SyncButton b : syncButtons) {
            if ((b.layout != null) && (b.button != null) && (b.activityClassName.equals(activity.getLocalClassName())))
                return;
        }
        SyncButton b = new SyncButton(activity, onSyncButtonPressed, gravity, x, y, defaultClient.getButtonStatus());
        syncButtons.add(b);
    }

    private static View.OnClickListener onSyncButtonPressed = new View.OnClickListener() {
        public void onClick(View v) {
            ButtonStatus buttonStatus = defaultClient.getButtonStatus();
            log(TAG, "SyncButton clicked: " + buttonStatus, LogLevel.info);
            if ( (buttonStatus == ButtonStatus.Offline) || (buttonStatus == ButtonStatus.Sync_Error) ) {
                Log.d(TAG, "Not connected: nothing to do");
                return;
            }
            defaultClient.startSync(SyncType.FullSync);
        }
    };

    public static ViewGroup getActivityRootView(Activity activity) {
        ViewGroup rootView = (ViewGroup) activity.findViewById(android.R.id.content);
        if (rootView == null)
            rootView = (ViewGroup) activity.getWindow().getDecorView().findViewById(android.R.id.content);
        return rootView;
    }

    private static ViewGroup getSrcRootView() {
        return getActivityRootView(srcActivity);
    }

    private View getSrcCurrentView() {
        return getSrcRootView().getChildAt(0);
    }

    private String translate(String s) {
        return s;
    }

    private void showAlert(final String msg) {
        final Context context = this;
        Log.d(TAG, msg);
        runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(context, translate(msg), Toast.LENGTH_LONG).show();
            }
        });
    }

    //////////////////////
    // Schema functions //
    //////////////////////
    private void saveSchema() throws JSONException {
        setItem("schema", schema.toJSON().toString());
    }

    private void loadSchema() throws JSONException {
        JSONObject jsonSchema = loadJSONItem("schema");
        if (jsonSchema != null)
            schema = new Schema(jsonSchema);
        else
            schema = null;
    }

    private void cacheSyncProfile(JSONObject syncRules) throws JSONException {
        syncProfile = new SyncProfile(syncRules);
        saveSyncProfile();
    }

    private void saveSyncProfile() throws JSONException {
        setItem("syncProfile", syncProfile.toJSON().toString());
    }

    private void loadSyncProfile() throws JSONException {
        JSONObject jsonSyncProfile = loadJSONItem("syncProfile");
        if (jsonSyncProfile != null)
            syncProfile = new SyncProfile(jsonSyncProfile.getJSONObject("syncRules"));
        else
            syncProfile = null;
    }

    private List<String> getTablesToSync() {
        List<String> result = new ArrayList<String>();
        if (syncProfile != null) {
            // Extract tables to sync from ssyncProfile's ync rules
            Iterator itSR = syncProfile.syncRules.iterator();
            while (itSR.hasNext()) {
                SyncRule sr = (SyncRule) itSR.next();
                if (((sr.clientSync > 0) && (syncType != SyncType.ClientReactiveSync)) || (sr.clientSync == 2))
                    result.add(sr.tableName);
            }
        } else {
            // If the user has no sync profile, the property tablesToSync should be set by the app to indicate which tables to sync.
            if ((syncType != SyncType.ClientReactiveSync) || (Boolean) getParam("reactiveSync")) {
                String sTablesToSync = getParam("tablesToSync").toString().replaceAll("\\s", "");        // remove possible spaces
                result = new ArrayList<String>(Arrays.asList(sTablesToSync.split(",")));
            }
        }
        return result;
    }

    private Boolean hasReactiveSync() {
        if (!getParam("reactiveSync").toString().equals("true"))
            return false;
        if (getReactiveSyncTables().size() > 0)
            return true;
        return false;
    }

    public List<String> getReactiveSyncTables() {
        List<String> result = new ArrayList<String>();
        if (syncProfile != null) {
            ListIterator<SyncRule> it = syncProfile.syncRules.listIterator();
            while (it.hasNext()) {
                SyncRule rule = it.next();
                if (rule.clientSync == 2)
                    result.add(rule.tableName);
            }
        }
        return result;
    }

    private Boolean upgradeNeeded(JSONObject schema) throws JSONException {

        Log.d(TAG, "db version: " + this.connector.getDBVersion());
        Log.d(TAG, "new schema version: " + schema.getString("version"));
        Boolean needed = ((getParam("autoUpgradeDB").toString() != "false") && (schema != null) && (schema.getString("version").compareTo(this.connector.getDBVersion()) > 0));
//        if ( !needed )
//            this.saveMustUpgrade(false);
        return needed;
    }

    ;

    private void upgradeDatabase(JSONObject schema) throws Exception {
        Log.d(TAG, "upgradeDatabase");
        showAlert("Database initialization...");
        connector.upgradeDatabase(schema);
        showAlert("Database initialization done !");
    }

    private void requestSchemaUpgrade() {
        sendRequestWithSessionId("\"getSchemaUpdate\":true");
    }

    private void requestSyncProfile() {
        sendRequestWithSessionId("\"getSyncProfile\":true");
    }

    ////////////////////
    // Sync functions //
    ////////////////////
    private void onSyncEnd() {
        if (syncType == SyncType.FullSync)
            recomputeModifiedTables();
        currentStep = Steps.Idle;
        lastSyncFailed = false;
        updateSyncButton();
        showAlert("Sync ended successfully");
    }

    /////////////////////////
    // Sync data to server //
    /////////////////////////
    private void flushDeletedUpserts(List<String> tables) throws JSONException {
        // Flush from changes tables all records that have been inserted and deleted since last client sync.
        ListIterator<String> it = tables.listIterator();
        while(it.hasNext()){
            String tableName = it.next();
            connector.flushDeletedUpserts(tableName);
        }
    }

    private void getAndSendClientChanges() throws JSONException {
        currentChanges = getClientChanges();
        sendClientChanges();
    }

    private Changes getClientChanges() throws JSONException {
        Log.d(TAG, "getClientChanges");
        currentStep = Steps.GettingClientChanges;
        List<String> tables = getTablesToSync();
        if ( tables.isEmpty() ){
            showAlert("Your sync profile has no table to sync ! Synchronization was aborted.");
            return null;
        }
        else
            Log.d(TAG, "tablesToSync=" + tables.toString());
        tables.retainAll(getModifiedTables());
        flushDeletedUpserts(tables);
        JSONObject deletes = getDeletes(tables);
        JSONObject upserts = getUpserts(tables);
        return new Changes(upserts, deletes);
    }

    public List<String> getModifiedTables() {
        // Modified tables are set by INSERT/UPDATE/DELETE triggers on main tables.
        // When set, they are used for reactive sync to restrain tables to sync.
        // Full sync doesn't take it into account (all tables are synched)
        return connector.getModifiedTables();
    }

    private JSONObject getDeletes(List<String> tables) throws JSONException {
        JSONObject result = new JSONObject();
        ListIterator<String> it = tables.listIterator();
        while(it.hasNext()){
            String tableName = it.next();
            result.put(tableName, connector.getDeletes(tableName));
        }
        return result;
    }

    private JSONObject getUpserts(List<String> tables) throws JSONException {
        JSONObject result = new JSONObject();
        ListIterator<String> it = tables.listIterator();
        while(it.hasNext()){
            String tableName = it.next();
            result.put(tableName, connector.getUpserts(tableName));
        }
        return result;
    }

    private void sendClientChanges() throws JSONException {
        currentStep = Steps.SendingClientChanges;
        String request = currentChanges.toJSON().toString();
        request = request.substring(1, request.length() - 1);       // Remove opening and closing JSON braces { }
        Log.d(TAG, "sendClientChanges: " + request);
        sendRequestWithSessionId(request);
    }

    private void resetSentChanges() throws JSONException {
        Log.d(TAG,"resetSentChanges");
        // Reset upserted/deleted states in database for all records that have just been sent (=currentChanges)
        if ( currentChanges == null )
            return;
        List<String> deletesTables = currentChanges.getDeletesTables();
        Iterator itDT = deletesTables.iterator();
        while ( itDT.hasNext() ){
            String tableName = (String)itDT.next();
            Log.d(TAG,"deleteTable: " + tableName);
            JSONArray keys = currentChanges.getDeletesKeys(tableName);
            Log.d(TAG,"keys: " + keys.toString());
            connector.resetDeletes(tableName, keys);
        }
        List<String> upsertsTables = currentChanges.getUpsertsTables();
        Iterator itUT = upsertsTables.iterator();
        while ( itUT.hasNext() ){
            String tableName = (String)itUT.next();
            Log.d(TAG,"upsertTable: " + tableName);
            String keyName = schema.findTableFromName(tableName).PK;
            JSONArray keys = currentChanges.getUpsertsKeys(tableName, keyName);
            Log.d(TAG,"keys: " + keys.toString());
            connector.resetUpserts(tableName, keys);
        }
        currentChanges = null;
        List<String> changesTables = new ArrayList<String>();
        changesTables.addAll(deletesTables);
        changesTables.addAll(upsertsTables);
    }

    public void onClientChanges(){
        if ( currentStep == Steps.Idle )
            startSync(SyncType.ClientReactiveSync);
    }

    public void recomputeModifiedTables(){
        List<String> tableNames = getModifiedTables();
        connector.recomputeModifiedTables(tableNames);
    }


    ///////////////////////////
    // Sync data from server //
    ///////////////////////////
    private void requestServerChanges(List<String> tables) throws JSONException {
        // Request changes from server on a list of tables (or all tables if the list is null)
        currentStep = Steps.RequestingServerChanges;
        List<String> tablesToSync = getTablesToSync();
        if ( tables != null )
            tablesToSync.retainAll(tables);
        String request = "\"getChanges\":[\"" + TextUtils.join("\",\"", tablesToSync.toArray()) + "\"]";
        Log.d(TAG, "requestServerChanges: " + request);
        sendRequestWithSessionId(request);
    }

    private void handleServerDeletes(JSONObject deletes) {
        if (deletes != null) {
            Iterator<String> tables = deletes.keys();
            while (tables.hasNext()) {
                String tableName = tables.next();
                Table table = schema.findTableFromName(tableName);
                if ( table == null ) {
                    Log.d(TAG, "Table " + tableName + " not found in schema");
                }
                JSONArray keys = null;
                try {
                    keys = deletes.getJSONArray(tableName);
                    Log.d(TAG, "Deletes keys " + keys);
                    connector.handleDeletes(table, keys);

                } catch (JSONException e) {
                    Log.d(TAG, e.getMessage());
                    e.printStackTrace();
                    return;
                }
            }
        }
    }

    private void handleServerUpdatesAndInserts(JSONObject updates, JSONObject inserts) throws JSONException {
        // Merge server updates and inserts into upserts
        JSONObject upserts;
        try {
            upserts = JsonUtils.deepMerge(updates, inserts);
        }catch(JSONException e){
            Log.d(TAG, e.getMessage());
            e.printStackTrace();
            return;
        }
        Log.d(TAG, "upserts=" + upserts);
        // Handle upserts with client's local data connector
        Iterator<String> tables = upserts.keys();
        while (tables.hasNext()) {
            String tableName = tables.next();
            Table table = schema.findTableFromName(tableName);
            if ( table == null ) {
                Log.d(TAG, "Table " + tableName + " not found in schema");
            }
            JSONArray tableUpserts;
            try {
                tableUpserts = upserts.getJSONArray(tableName);

            } catch (JSONException e) {
                Log.d(TAG, e.getMessage());
                e.printStackTrace();
                return;
            }
            Log.d(TAG, "Upserts " + tableUpserts);
            connector.handleUpserts(table, tableUpserts);
        }
    }

    private void endServerSync() throws JSONException {
        // Send a endServerSync request with the list of tables handled by client.
        //currentStep = Steps.FinishingSync;
        List<String> tablesToSync = getTablesToSync();
        if (handledTables.size() > 0)
            tablesToSync.retainAll(handledTables);
        String request = "\"endServerSync\":[\"" + TextUtils.join("\",\"", tablesToSync.toArray()) + "\"]";
        Log.d(TAG, "endServerSync: " + request);
        sendRequestWithSessionId(request);
    }

    public static void log(String tag, String msg, LogLevel logLevel){
        Log.d(tag,msg);
    }
}
