package com.syncproxy.syncclient;

import android.content.Context;
import android.database.DatabaseErrorHandler;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDatabase.CursorFactory;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import java.lang.reflect.Field;

// SyncProxySQLiteOpenHelper is a SQLiteOpenHelper-replacement class that prevents creating several SQLiteDatabase objects for the same database.
public class SyncProxySQLiteOpenHelper extends SQLiteOpenHelper {
    private static final String TAG = SyncProxySQLiteOpenHelper.class.getSimpleName();
    public static SQLiteOpenHelper mInstance;
    private final String superClassName = "android.database.sqlite.SQLiteOpenHelper";
    private Class<?> superClass = null;

    public SyncProxySQLiteOpenHelper(Context context, String name, int version) {
        this(context, name, null, version);
        Log.d(TAG, "constructor");
    }

    public SyncProxySQLiteOpenHelper(Context context, String name, CursorFactory factory, int version) {
        super(context, name, factory, version);
        // Enable only 1 instance of SQLiteOpenHelper
        if (mInstance == null )
            mInstance = this;
        else
            cloneHelper(mInstance);
    }

    public SyncProxySQLiteOpenHelper(Context context, String name, CursorFactory factory, int version, DatabaseErrorHandler errorHandler) {
        super(context, name, factory, version, null);
        // Enable only 1 instance of SQLiteOpenHelper
        if (mInstance == null )
            mInstance = this;
        else
            cloneHelper(mInstance);
    }

    public void cloneHelper(SQLiteOpenHelper helper) {
        Log.d(TAG, "cloneHelper");
        if ( superClass == null )
            try {
                superClass = Class.forName(superClassName);
            } catch (ClassNotFoundException e) {
                Log.d(TAG, e.getLocalizedMessage());
                e.printStackTrace();
            }
        setVal("mContext", getVal("mContext", helper));
        setVal("mName", getVal("mName", helper));
        setVal("mNewVersion", getVal("mNewVersion", helper));
        setVal("mMinimumSupportedVersion", getVal("mMinimumSupportedVersion", helper));
        setVal("mDatabase", getVal("mDatabase", helper));
        //setVal("mOpenParamsBuilder", getVal("mOpenParamsBuilder", helper));       // Apparently undefined in SQLiteOpenHelper ?
    }

    Object getVal(String fieldName, SQLiteOpenHelper helper){
        Field f = null;
        try {
            f =  superClass.getDeclaredField(fieldName) ;

        } catch (NoSuchFieldException e) {
            Log.d(TAG, e.getLocalizedMessage());
            e.printStackTrace();
        }
        f.setAccessible(true);
        try {
            return f.get(helper);
        } catch (IllegalAccessException e) {
            Log.d(TAG, e.getLocalizedMessage());
            e.printStackTrace();
        }
        return null;
    }

    void setVal(String fieldName, Object val){
       Field f = null;
        try {
            f =  superClass.getDeclaredField(fieldName) ;
        } catch (NoSuchFieldException e) {
            Log.d(TAG, e.getLocalizedMessage());
            e.printStackTrace();
        }
        f.setAccessible(true);
        try {
            f.set( this, val);
        } catch (IllegalAccessException e) {
            Log.d(TAG, e.getLocalizedMessage());
            e.printStackTrace();
        }
    }

    @Override
    public SQLiteDatabase getWritableDatabase() {
        return super.getWritableDatabase();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Log.d(TAG, "SyncProxySQLiteOpenHelper.onCreate");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.d(TAG, "SyncProxySQLiteOpenHelper.onUpgrade");

    }
}