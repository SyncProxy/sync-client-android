
# Introduction
SyncProxy client for Android enables one-single line of code implementation of synchronization for Android offline applications using embedded database (SQLite...). Used with the SyncProxy server (www.syncproxy.com) to access the backend database (MySQL, SQL Server, MongoDB...), this is the shortest way to make mobile offline applications synchronize bi-directionally in realtime using reactive sync technology.

# Installation
```
$ git clone https://github.com/syncproxy/sync-client-android
```
Then import the **SyncProxy.AAR** library file to your application project in Android Studio: **File/ New / New module... / Import .JAR/.AAR package**.
# Example with SQLite
The SyncProxy client is instanciated in the main activity's onCreate() handler:
```java
import com.syncproxy.syncclient.SyncClient;
...
@Override  
protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);  
    SyncClient.startObservation(this, "{'proxyId':'<your SyncProxy Id>', 'connectorType':'SQLite', 'dbName':'<local db name>'}" ).showSyncButton(this);
}
```
(You get your proxyId on my.syncproxy.com after creating the sync proxy).

Then, if your application has several activities, you can display the sync button in any activity:
```java
import com.syncproxy.syncclient.SyncClient;
...
@Override
protected void onStart() {
    super.onStart();
    SyncClient.showSyncButton(this);
}

```
A sync button will automatically be added on top of your activity and the sync client will start monitoring your SQLite database in the background.

**Important:**
Du to the limited build-in support of concurrency within SQLite engine, simultaneous multiple connections to a SQLite database generally result in app crashes or unpredictable results. Therefore, SyncProxy library comes with a concurrency-safe, thread-safe replacement class for the SQLiteOpenHelper. All you have to do is replace all calls to **SQLiteOpenHelper** with **SyncProxySQLiteOpenHelper** in your application's code.
## Custom params
SyncClient's startObservation() function can be invoked with custom params:

**proxyID (mandatory)**  
Id attributed by SyncProxy to  your proxy on creation

**connectorType**  
values: "SQLite" (more to come in a near future)
default: "SQLite"

**dbName**  
Name of your embedded database in mobile app.  
default: "SyncProxy"

**protocol**  
values: "ws" (websocket) or "wss" (secured websoket)  
default: "wss"

**serverUrl**  
Url of the server hosting SyncProxy  
default: "my.syncproxy.com"

**serverPort**  
Port listened on by SyncProxy server  
default value: 4501

**autoUpgradeDB**  
values: "true", "false"
If true, the embedded database's structure will be automatically upgraded (if this is relevant to the type of database) during sync after a database schema update.
Set to false if application creates and upgrades database structures by itself.  
default: "true"

**reactiveSync**  
values: "true", "false"
If true, enables reactive sync. Reactivity for each table + direction (server->client and client->server) is configured on SyncProxy admin console  
default: "true"

**tablesToSync**  
When using server-side Auto Backendless database or NoSQL database without server database schema, you have the ability to discover schema from client's data. In that case, attribute tablesToSync must be set with the list of tables to sync from client to server. 
default: ""

**welcomeMessage**  
Message that will popup in the app before user synchronizes.  
default: "To begin, please press Sync button"

## Testing
Like us, test your mobile and progressive web apps with

[<img src="https://p14.zdusercontent.com/attachment/1015988/xhbf3TBMImSwSmvre7tih36sU?token=eyJhbGciOiJkaXIiLCJlbmMiOiJBMTI4Q0JDLUhTMjU2In0..COsnFxS-SgN1cn9JihtFaw.N8jUlNaqw59Ds-CRovWcf4miZx5tIU9Lhs8KEQ2JOEZr5XwRQbo2K2LPS3rUndakRvv6z-dnHz8spRW-umW1dyBqrx43LC_EhKXSSdrXnPE2Byjq4yDPA1Y0HMVHfZLxkGz3mXVyqb2zNRIotsSNjMEluIOcXjXpInIz2iOnt2GarlXRcGOp5ssQMUJ4vNcdihvIOxY3lYUkjDoWlnWgyMfqVn2eBtZVXPrm52gjfexXwi4Ct-MGYtQC1iZJ5iiAbCjsyeew51v8ZJqE5lYM7eQsoLx2No7mkGuCUnl6iDg.-VRGokd_4kLCE3N0xBxArw" width="300px">](http://www.browserstack.com)

## Documentation
Read our tutorial on how to setup SyncProxy client for Android applications 
https://github.com/syncproxy/syncproxy-quickstart-android