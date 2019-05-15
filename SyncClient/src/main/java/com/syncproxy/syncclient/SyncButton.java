package com.syncproxy.syncclient;

import android.app.Activity;
import android.content.Context;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.LinearInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.ImageButton;
import android.widget.RelativeLayout;

import java.util.List;

public class SyncButton{
    private static final String TAG = "SyncButton";
    private SyncClient.ButtonStatus currentStatus = SyncClient.ButtonStatus.Sync_Error;
    private Boolean rotating = false;

    // Implements a sync button and its parent layout, destined to be inserted within existing activity.
    public String activityClassName = null;
    public RelativeLayout layout = null;
    public ImageButton button = null;
    // Persistent position across same-activity launches
    public int x = -1;
    public int y = -1;

    public SyncButton(Activity activity, View.OnClickListener onClick, int gravity, int x, int y, SyncClient.ButtonStatus buttonStatus){
        this.activityClassName = activity.getLocalClassName();
        createButton(activity, onClick, gravity, x, y, buttonStatus);
    }
    private void createButton(Activity activity, View.OnClickListener onClick, int gravity, int x, int y, SyncClient.ButtonStatus buttonStatus){
        Context context = new android.support.v7.view.ContextThemeWrapper(activity.getApplicationContext(), R.style.AppTheme);
        LayoutInflater inflater = LayoutInflater.from(context);
        ViewGroup mainView = SyncClient.getActivityRootView(activity);
        RelativeLayout l = (RelativeLayout) inflater.inflate(R.layout.sync_client_layout, null, false);
        if ( gravity == -1 )
            gravity = Gravity.CENTER | Gravity.BOTTOM;
        if ( gravity != 0 )
            l.setGravity(gravity);
        mainView.addView(l);
        button = (ImageButton) inflater.inflate(R.layout.sync_button, null, false);
        if ( x != -1 )
            button.setX(x);
        if ( y != -1 )
            button.setY(y);
        button.setOnClickListener(onClick);
        l.addView(button);
        setStatus(buttonStatus, activity, false);
    }

    public void setStatus(SyncClient.ButtonStatus status, Activity activity, Boolean rotate) {
        SyncClient.log(TAG, "buttonStatus=" + status, SyncClient.LogLevel.info);
//        if ( currentStatus != status ) {
            switch (status) {
                case Online:
                    button.setBackgroundResource(R.mipmap.ic_syncbutton_online);
                    break;
                case Offline:
                    button.setBackgroundResource(R.mipmap.ic_syncbutton_offline);
                    break;
                case Sync_Auto:
                    button.setBackgroundResource(R.mipmap.ic_syncbutton_sync_auto);
                    break;
                case Sync_Not_Authenticated:
                    button.setBackgroundResource(R.mipmap.ic_syncbutton_sync_noconnection);
                    break;
                case Sync_OK:
                    button.setBackgroundResource(R.mipmap.ic_syncbutton_sync_ok);
                    break;
                case Sync_Error:
                    button.setBackgroundResource(R.mipmap.ic_syncbutton_sync_error);
                    break;
                case Sync_In_Progress:
                    button.setBackgroundResource(R.mipmap.ic_syncbutton_sync_ok);
                    break;
                case Sync_Auto_In_Progress:
                    button.setBackgroundResource(R.mipmap.ic_syncbutton_sync_auto);
                    break;
            }
//        }
        if ( rotate && !rotating ) {
            Log.d(TAG, "Start animation");
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    RotateAnimation rotation = new RotateAnimation(0, 360, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
                    rotation.setDuration(1000);
                    rotation.setInterpolator(new LinearInterpolator());
                    rotation.setRepeatCount(Animation.INFINITE);
//                    button.setAnimation(rotation);
                    button.startAnimation(rotation);
                }
            });
        }
        else if ( !rotate ){
            Log.d(TAG, "Stop animation");
            activity.runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    button.clearAnimation();
                }
            });
        }
        rotating = rotate;
        currentStatus = status;
    }

    public static void setStatusForAllButtons(List<SyncButton> syncButtons, SyncClient.ButtonStatus status, Activity activity, Boolean rotate){
        // Set image for all active sync buttons
        for ( SyncButton b: syncButtons)
            b.setStatus(status, activity, rotate);
    }

}